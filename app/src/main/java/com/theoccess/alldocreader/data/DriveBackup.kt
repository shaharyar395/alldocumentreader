package com.theoccess.alldocreader.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * "Save to Google Drive": when switched on in Settings, every file the app saves or converts
 * (everything that goes through [SavedFiles]) is also copied to a folder called
 * "All Document Reader" in the user's Google Drive. Edited files replace their earlier copy.
 *
 * Uses Google sign-in with the "drive.file" permission (the app only sees files it created
 * itself) and the Drive REST API. Uploads that fail (no internet…) are kept and retried
 * later — on the next save and on the next app start.
 *
 * One-time setup in Google Cloud Console (same Google account as Play Console is easiest):
 *  1. Create a project → APIs & Services → enable "Google Drive API".
 *  2. OAuth consent screen: External, app name, support e-mail; add the scope
 *     .../auth/drive.file; while in "Testing" add your Gmail as a test user.
 *  3. Credentials → Create OAuth client ID → Android: package com.theoccess.alldocreader and
 *     the SHA-1 of your signing key (debug key for Android Studio builds; the Play
 *     "App signing key" SHA-1 for builds installed from Google Play — add one client for each).
 */
object DriveBackup {

    private const val TAG = "DriveBackup"
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER_NAME = "All Document Reader"
    private const val KEY_ON = "drive_backup_on"
    private const val KEY_FOLDER = "drive_folder_id"
    private const val KEY_IDS = "drive_file_ids"
    private const val KEY_PENDING = "drive_pending"

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var enabled: Boolean
        get() = Prefs.raw.getBoolean(KEY_ON, false)
        private set(v) = Prefs.raw.edit().putBoolean(KEY_ON, v).apply()

    /** Set when Google needs the user to sign in again (shown in Settings). */
    @Volatile
    var needsSignIn = false
        private set

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    // ------------------------------------------------------------------ switching on / off

    /**
     * Asks Google for Drive access. If the user has to pick an account / agree, [launcher]
     * shows Google's screen and the result must be passed to [onSignInResult].
     */
    fun turnOn(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>, done: (ok: Boolean, error: String?) -> Unit) {
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    pendingDone = done
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    activated(activity)
                    done(true, null)
                }
            }
            .addOnFailureListener { e -> done(false, describe(e)) }
    }

    private var pendingDone: ((Boolean, String?) -> Unit)? = null

    fun onSignInResult(activity: Activity, resultCode: Int, data: Intent?) {
        val done = pendingDone
        pendingDone = null
        if (resultCode != Activity.RESULT_OK || data == null) { done?.invoke(false, null); return }
        try {
            val r: AuthorizationResult = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            if (r.accessToken != null) { activated(activity); done?.invoke(true, null) }
            else done?.invoke(false, null)
        } catch (e: ApiException) {
            done?.invoke(false, describe(e))
        }
    }

    private fun activated(context: Context) {
        enabled = true
        needsSignIn = false
        flush(context.applicationContext)
    }

    fun turnOff() {
        enabled = false
        Prefs.raw.edit().remove(KEY_PENDING).apply()
    }

    private fun describe(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return if (code == 10) "Google sign-in is not set up for this app yet (OAuth client for this package / SHA-1 missing in Google Cloud Console)."
        else e.localizedMessage ?: e.toString()
    }

    // ------------------------------------------------------------------ uploading

    /** Called by [SavedFiles] for every saved / converted file. */
    fun onSaved(context: Context, file: File) {
        if (!enabled) return
        val pending = Prefs.raw.getStringSet(KEY_PENDING, emptySet()).orEmpty() + file.absolutePath
        Prefs.raw.edit().putStringSet(KEY_PENDING, pending).apply()
        flush(context.applicationContext)
    }

    /** Uploads everything that is waiting (on a background thread). */
    fun flush(context: Context) {
        if (!enabled) return
        worker.execute {
            val paths = Prefs.raw.getStringSet(KEY_PENDING, emptySet()).orEmpty()
            if (paths.isEmpty()) return@execute
            val token = try {
                val r = Tasks.await(Identity.getAuthorizationClient(context).authorize(request()))
                if (r.hasResolution()) { needsSignIn = true; null } else r.accessToken
            } catch (e: Exception) {
                Log.w(TAG, "Drive authorization failed", e)
                null
            } ?: return@execute
            needsSignIn = false
            val folder = try { folderId(token) } catch (e: Exception) { Log.w(TAG, "Drive folder failed", e); null } ?: return@execute
            val done = HashSet<String>()
            for (path in paths) {
                val f = File(path)
                if (!f.exists()) { done += path; continue }
                try {
                    upload(token, folder, f)
                    done += path
                } catch (e: Exception) {
                    Log.w(TAG, "Upload failed: ${f.name}", e)
                }
            }
            val left = Prefs.raw.getStringSet(KEY_PENDING, emptySet()).orEmpty() - done
            Prefs.raw.edit().putStringSet(KEY_PENDING, left).apply()
            if (done.isNotEmpty()) Log.i(TAG, "Saved ${done.size} file(s) to Google Drive")
        }
    }

    /** The "All Document Reader" folder in Drive (created on first use). */
    private fun folderId(token: String): String {
        Prefs.raw.getString(KEY_FOLDER, null)?.let { id ->
            val (code, body) = http("GET", "https://www.googleapis.com/drive/v3/files/$id?fields=id,trashed", token)
            if (code == 200 && !JSONObject(body).optBoolean("trashed")) return id
        }
        val q = URLEncoder.encode("name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8")
        val (sc, sb) = http("GET", "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&spaces=drive", token)
        if (sc == 200) {
            val files = JSONObject(sb).optJSONArray("files")
            if (files != null && files.length() > 0) return files.getJSONObject(0).getString("id").also { save(KEY_FOLDER, it) }
        }
        val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", "application/vnd.google-apps.folder").toString()
        val (cc, cb) = http("POST", "https://www.googleapis.com/drive/v3/files?fields=id", token, "application/json; charset=UTF-8", meta.toByteArray())
        check(cc in 200..299) { "create folder: $cc $cb" }
        return JSONObject(cb).getString("id").also { save(KEY_FOLDER, it) }
    }

    /** New file → created in the folder; a file uploaded before → its Drive copy is replaced. */
    private fun upload(token: String, folder: String, file: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val ids = JSONObject(Prefs.raw.getString(KEY_IDS, "{}") ?: "{}")
        val known = ids.optString(file.absolutePath).takeIf { it.isNotEmpty() }
        if (known != null) {
            val (code, _) = http("PATCH", "https://www.googleapis.com/upload/drive/v3/files/$known?uploadType=media", token, mime, file.readBytes())
            if (code in 200..299) return
            if (code != 404) error("update ${file.name}: $code")
        }
        val boundary = "adr_" + System.currentTimeMillis()
        val meta = JSONObject().put("name", file.name).put("parents", org.json.JSONArray().put(folder)).toString()
        val body = java.io.ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
            write(file.readBytes())
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val (code, resp) = http("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", token,
            "multipart/related; boundary=$boundary", body)
        check(code in 200..299) { "upload ${file.name}: $code $resp" }
        ids.put(file.absolutePath, JSONObject(resp).getString("id"))
        save(KEY_IDS, ids.toString())
    }

    private fun save(key: String, value: String) = Prefs.raw.edit().putString(key, value).apply()

    /** Small HTTP helper; PATCH is sent as POST + X-HTTP-Method-Override (HttpURLConnection has no PATCH). */
    private fun http(method: String, url: String, token: String, contentType: String? = null, body: ByteArray? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            if (method == "PATCH") {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else c.requestMethod = method
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally {
            c.disconnect()
        }
    }

    /** Main-thread helper for callers that want to show a message. */
    fun post(block: () -> Unit) { main.post(block) }
}
