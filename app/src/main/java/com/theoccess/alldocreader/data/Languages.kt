package com.theoccess.alldocreader.data

/** Languages offered on the Language screen. An empty tag means "follow the system". */
data class AppLanguage(val tag: String, val displayName: String)

object Languages {
    const val DEFAULT_TAG = ""

    val all = listOf(
        AppLanguage(DEFAULT_TAG, ""), // label comes from R.string.language_default
        AppLanguage("en", "English"),
        AppLanguage("ar", "العربية"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("es", "Español"),
        AppLanguage("fa", "فارسی"),
        AppLanguage("fr", "Français"),
        AppLanguage("id", "Indonesia"),
        AppLanguage("it", "Italiano"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("ms", "Melayu"),
        AppLanguage("pt", "Português"),
        AppLanguage("ru", "Русский"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("uz", "O'zbekcha"),
        AppLanguage("th", "ภาษาไทย"),
        AppLanguage("uk", "Українська"),
        AppLanguage("pl", "Polski"),
        AppLanguage("tl", "Filipino"),
        AppLanguage("zh-TW", "繁體中文"),
        AppLanguage("ur", "اردو"),
        AppLanguage("zh-CN", "简体中文")
    )

    /** Maps whatever AppCompat reports back to one of our tags. */
    fun match(currentTags: String): String {
        if (currentTags.isBlank()) return DEFAULT_TAG
        val first = currentTags.split(",").first().trim()
        all.firstOrNull { it.tag.equals(first, ignoreCase = true) }?.let { return it.tag }
        val lang = first.substringBefore('-').lowercase()
        // Android may report Indonesian as legacy "in", Filipino as "fil"
        val normalized = when (lang) { "in" -> "id"; "fil" -> "tl"; else -> lang }
        return all.firstOrNull { it.tag.substringBefore('-') == normalized }?.tag ?: DEFAULT_TAG
    }
}
