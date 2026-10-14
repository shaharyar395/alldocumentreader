package com.theoccess.alldocreader.ui.viewer

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.floor

/**
 * Excel viewer: reads .xlsx / .xlsm / .xltx (Office Open XML), .ods (OpenDocument) and .csv
 * and turns every sheet into a grid like a spreadsheet app — column letters across the top,
 * row numbers down the side, the sheet's own column widths, shared strings, numbers, dates,
 * percentages and booleans. Each sheet starts on a new page with its name as a heading; pages
 * are as wide as the sheet so nothing is squeezed (pinch to zoom). The grid can also be
 * searched and converted to PDF like any other document.
 *
 * Old binary .xls files are not read here (they open in another installed app instead).
 */
object SheetParser {

    private val EXTENSIONS = setOf("xlsx", "xlsm", "xltx", "xltm", "ods", "csv", "tsv")
    private const val MAX_ROWS = 3000
    private const val MAX_COLS = 40
    private const val DEFAULT_COL_PT = 64f
    private const val HEADER_COL_PT = 34f
    private const val MARGIN = 24f

    fun canOpen(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** One sheet: name, cell texts by (row, col) 0-based, column widths in points. */
    private class Sheet(val name: String, val cells: MutableMap<Long, String> = HashMap(), val widths: MutableMap<Int, Float> = HashMap()) {
        var maxRow = -1
        var maxCol = -1
        fun put(r: Int, c: Int, v: String) {
            if (v.isEmpty() || r >= MAX_ROWS || c >= MAX_COLS) return
            cells[key(r, c)] = v
            if (r > maxRow) maxRow = r
            if (c > maxCol) maxCol = c
        }
    }

    private fun key(r: Int, c: Int) = r.toLong() * 100_000L + c

    fun parse(file: File): ParsedDoc {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val sheets = when (ext) {
            "csv", "tsv" -> listOf(readCsv(file, if (ext == "tsv") '\t' else null))
            "ods" -> readOds(file)
            else -> readXlsx(file)
        }
        return layout(sheets)
    }

    // ------------------------------------------------------------------ layout

    private fun layout(sheets: List<Sheet>): ParsedDoc {
        val blocks = ArrayList<DocBlock>()
        var widest = 400f
        sheets.forEachIndexed { si, sh ->
            if (si > 0) blocks += DocBlock.PageBreak
            val cols = (sh.maxCol + 1).coerceAtLeast(1)
            val widths = listOf(HEADER_COL_PT) + (0 until cols).map { sh.widths[it] ?: DEFAULT_COL_PT }
            val total = widths.sum()
            widest = maxOf(widest, total)
            if (sheets.size > 1 || sh.name.isNotEmpty()) {
                val title = SpannableString(sh.name.ifEmpty { "Sheet${si + 1}" })
                title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                blocks += DocBlock.Para(title, Layout.Alignment.ALIGN_NORMAL, spaceAfter = 6f)
            }
            if (sh.maxRow < 0) {
                blocks += DocBlock.Para("(empty)")
                return@forEachIndexed
            }
            val rows = ArrayList<List<CharSequence>>()
            rows += listOf<CharSequence>(header("")) + (0 until cols).map { header(colName(it)) }
            for (r in 0..sh.maxRow) {
                rows += listOf<CharSequence>(header("${r + 1}")) + (0 until cols).map { c -> sh.cells[key(r, c)] ?: "" }
            }
            blocks += DocBlock.Table(rows, widths.map { it / total })
        }
        // pages as wide as the widest sheet (+ margins), A4-like height
        val w = widest + 2 * MARGIN
        val h = maxOf(842f, w * 1.414f)
        return ParsedDoc(blocks, PageGeometry(w, h, MARGIN, MARGIN, MARGIN, MARGIN))
    }

    private fun header(s: String): CharSequence {
        val t = SpannableString(s)
        if (s.isNotEmpty()) {
            t.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            t.setSpan(ForegroundColorSpan(0xFF5F6670.toInt()), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return t
    }

    fun colName(c: Int): String {
        var n = c + 1
        val sb = StringBuilder()
        while (n > 0) { val m = (n - 1) % 26; sb.append('A' + m); n = (n - 1) / 26 }
        return sb.reverse().toString()
    }

    /** "BC12" → (11, 54). */
    private fun ref(r: String): Pair<Int, Int>? {
        var i = 0
        var col = 0
        while (i < r.length && r[i].isLetter()) { col = col * 26 + (r[i].uppercaseChar() - 'A' + 1); i++ }
        val row = r.substring(i).toIntOrNull() ?: return null
        if (col == 0) return null
        return (row - 1) to (col - 1)
    }

    private fun parser(input: InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    private fun local(name: String) = name.substringAfter(':')

    // ------------------------------------------------------------------ xlsx

    private fun readXlsx(file: File): List<Sheet> = ZipFile(file).use { zip ->
        fun open(path: String) = zip.getEntry(path)?.let { zip.getInputStream(it) }
        val shared = open("xl/sharedStrings.xml")?.use { readShared(it) } ?: emptyList()
        val styles = open("xl/styles.xml")?.use { readStyles(it) } ?: emptyList()
        val date1904 = BooleanArray(1)
        val rels = HashMap<String, String>()
        open("xl/_rels/workbook.xml.rels")?.use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && local(p.name) == "Relationship") {
                    val id = p.getAttributeValue(null, "Id") ?: continue
                    val t = p.getAttributeValue(null, "Target") ?: continue
                    rels[id] = if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("./")
                }
            }
        }
        val list = ArrayList<Pair<String, String>>() // name → path
        open("xl/workbook.xml")?.use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (local(p.name)) {
                    "workbookPr" -> date1904[0] = p.getAttributeValue(null, "date1904").let { it == "1" || it == "true" }
                    "sheet" -> {
                        val name = p.getAttributeValue(null, "name") ?: ""
                        val state = p.getAttributeValue(null, "state")
                        if (state == "hidden" || state == "veryHidden") continue
                        var id: String? = null
                        for (i in 0 until p.attributeCount) if (local(p.getAttributeName(i)) == "id") id = p.getAttributeValue(i)
                        val path = id?.let { rels[it] } ?: continue
                        list += name to path
                    }
                }
            }
        }
        if (list.isEmpty()) {
            // no workbook info: take the worksheets in order
            zip.entries().asSequence().map { it.name }.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted().forEachIndexed { i, n -> list += "Sheet${i + 1}" to n }
        }
        list.mapNotNull { (name, path) ->
            open(path)?.use { readXlsxSheet(it, name, shared, styles, date1904[0]) }
        }.ifEmpty { throw IllegalArgumentException("No sheets") }
    }

    private fun readShared(input: InputStream): List<String> {
        val out = ArrayList<String>()
        val p = parser(input)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var inRPh = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> inT = true
                    "rPh" -> inRPh = true   // phonetic guides: not shown
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inRPh) sb.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "si" -> { inSi = false; out += sb.toString() }
                    "t" -> inT = false
                    "rPh" -> inRPh = false
                }
            }
        }
        return out
    }

    /** Kind of number format per cell style index. */
    private enum class Fmt { GENERAL, DATE, TIME, DATETIME, PERCENT }

    private fun readStyles(input: InputStream): List<Fmt> {
        val custom = HashMap<Int, String>()
        val xfs = ArrayList<Int>()
        val p = parser(input)
        var inCellXfs = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (local(p.name)) {
                "numFmt" -> {
                    val id = p.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                    val code = p.getAttributeValue(null, "formatCode")
                    if (id != null && code != null) custom[id] = code
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) xfs += p.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
            } else if (p.eventType == XmlPullParser.END_TAG && local(p.name) == "cellXfs") inCellXfs = false
        }
        return xfs.map { id -> kind(id, custom[id]) }
    }

    private fun kind(id: Int, code: String?): Fmt {
        when (id) {
            in 14..17, in 27..36, in 50..58 -> return Fmt.DATE
            in 18..21, 45, 46, 47 -> return Fmt.TIME
            22 -> return Fmt.DATETIME
            9, 10 -> return Fmt.PERCENT
        }
        val c = code ?: return Fmt.GENERAL
        // ignore quoted text, [colour] / [$-locale] parts and escaped characters
        val clean = c.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^]]*]"), "").replace(Regex("\\\\."), "").lowercase()
        val hasDate = clean.contains('y') || clean.contains('d') || (clean.contains('m') && !clean.contains('h') && !clean.contains('s'))
        val hasTime = clean.contains('h') || clean.contains('s')
        return when {
            clean.contains('%') -> Fmt.PERCENT
            hasDate && hasTime -> Fmt.DATETIME
            hasDate && (clean.contains('y') || clean.contains('d')) -> Fmt.DATE
            hasTime -> Fmt.TIME
            else -> Fmt.GENERAL
        }
    }

    private fun readXlsxSheet(input: InputStream, name: String, shared: List<String>, styles: List<Fmt>, date1904: Boolean): Sheet {
        val sh = Sheet(name)
        val p = parser(input)
        var row = -1
        var col = -1
        var type: String? = null
        var style = 0
        var value: StringBuilder? = null
        var inline = StringBuilder()
        var inV = false
        var inIs = false
        var inT = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "col" -> {
                        val min = p.getAttributeValue(null, "min")?.toIntOrNull() ?: continue
                        val max = p.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                        val w = p.getAttributeValue(null, "width")?.toFloatOrNull() ?: continue
                        val hidden = p.getAttributeValue(null, "hidden") == "1"
                        // Excel width = characters of the default font (~5.25 pt each, + padding)
                        val pt = if (hidden) 0f else (w * 5.6f + 4f).coerceIn(18f, 420f)
                        for (c in (min - 1) until minOf(max, MAX_COLS)) sh.widths[c] = pt
                    }
                    "row" -> {
                        row = (p.getAttributeValue(null, "r")?.toIntOrNull()?.minus(1)) ?: (row + 1)
                        col = -1
                    }
                    "c" -> {
                        val r = p.getAttributeValue(null, "r")?.let { ref(it) }
                        if (r != null) { row = r.first; col = r.second } else col++
                        type = p.getAttributeValue(null, "t")
                        style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                        value = StringBuilder()
                        inline = StringBuilder()
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> {
                    if (inV) value?.append(p.text)
                    else if (inIs && inT) inline.append(p.text)
                }
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v" -> inV = false
                    "is" -> inIs = false
                    "t" -> inT = false
                    "c" -> {
                        val v = value?.toString().orEmpty()
                        val text = when (type) {
                            "s" -> v.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "inlineStr" -> inline.toString()
                            "b" -> if (v.trim() == "1") "TRUE" else "FALSE"
                            "str", "e" -> v
                            else -> number(v, styles.getOrElse(style) { Fmt.GENERAL }, date1904)
                        }
                        if (row >= 0 && col >= 0) sh.put(row, col, text)
                        value = null
                    }
                }
            }
        }
        return sh
    }

    // ------------------------------------------------------------------ numbers / dates

    private fun number(raw: String, fmt: Fmt, date1904: Boolean): String {
        val d = raw.trim().toDoubleOrNull() ?: return raw
        return when (fmt) {
            Fmt.PERCENT -> plain(d * 100) + "%"
            Fmt.DATE, Fmt.TIME, Fmt.DATETIME -> serialDate(d, fmt, date1904)
            Fmt.GENERAL -> plain(d)
        }
    }

    /** 12.0 → "12", 0.1 + 0.2 → "0.3", big / tiny numbers like Excel's General format. */
    private fun plain(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return d.toString()
        if (d == floor(d) && abs(d) < 1e15) return String.format(Locale.US, "%.0f", d)
        if (abs(d) >= 1e11 || abs(d) < 1e-9) return String.format(Locale.US, "%.5E", d).replace(Regex("0+E"), "E").replace(".E", "E")
        val s = String.format(Locale.US, "%.10f", d).trimEnd('0').trimEnd('.')
        return java.math.BigDecimal(s).round(java.math.MathContext(11)).stripTrailingZeros().toPlainString()
    }

    private fun serialDate(serial: Double, fmt: Fmt, date1904: Boolean): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        if (date1904) cal.set(1904, 0, 1) else cal.set(1899, 11, 30)
        val days = floor(serial).toInt()
        val ms = ((serial - days) * 86_400_000.0 + 0.5).toLong()
        cal.add(Calendar.DATE, days)
        cal.add(Calendar.MILLISECOND, ms.toInt())
        val pattern = when (fmt) {
            Fmt.TIME -> "HH:mm:ss"
            Fmt.DATETIME -> "yyyy-MM-dd HH:mm"
            else -> "yyyy-MM-dd"
        }
        return SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(cal.time)
    }

    // ------------------------------------------------------------------ ods

    private fun readOds(file: File): List<Sheet> = ZipFile(file).use { zip ->
        val entry = zip.getEntry("content.xml") ?: throw IllegalArgumentException("Not a spreadsheet")
        zip.getInputStream(entry).use { input ->
            val out = ArrayList<Sheet>()
            val p = parser(input)
            var sh: Sheet? = null
            var row = -1
            var col = 0
            var rowRepeat = 1
            var cellRepeat = 1
            var cellValue: String? = null
            val text = StringBuilder()
            var inCell = false
            var paragraphs = 0
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "table:table" -> { sh = Sheet(p.getAttributeValue(null, "table:name") ?: ""); row = -1 }
                        "table:table-row" -> {
                            row++
                            col = 0
                            rowRepeat = (p.getAttributeValue(null, "table:number-rows-repeated")?.toIntOrNull() ?: 1).coerceAtMost(MAX_ROWS)
                        }
                        "table:table-cell", "table:covered-table-cell" -> {
                            inCell = true
                            text.setLength(0)
                            paragraphs = 0
                            cellRepeat = (p.getAttributeValue(null, "table:number-columns-repeated")?.toIntOrNull() ?: 1).coerceAtMost(MAX_COLS)
                            val type = p.getAttributeValue(null, "office:value-type")
                            cellValue = when (type) {
                                "float", "currency" -> p.getAttributeValue(null, "office:value")?.toDoubleOrNull()?.let { plain(it) }
                                "percentage" -> p.getAttributeValue(null, "office:value")?.toDoubleOrNull()?.let { plain(it * 100) + "%" }
                                "date" -> p.getAttributeValue(null, "office:date-value")?.replace('T', ' ')?.substringBefore('.')
                                "boolean" -> p.getAttributeValue(null, "office:boolean-value")?.uppercase()
                                else -> null
                            }
                        }
                        "text:p" -> if (inCell) { if (paragraphs++ > 0) text.append('\n') }
                        "text:s" -> if (inCell) text.append(" ".repeat(p.getAttributeValue(null, "text:c")?.toIntOrNull() ?: 1))
                        "text:tab" -> if (inCell) text.append('\t')
                    }
                    XmlPullParser.TEXT -> if (inCell) text.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "table:table-cell", "table:covered-table-cell" -> {
                            inCell = false
                            // displayed text first (keeps the sheet's own number / date formatting)
                            val v = text.toString().ifEmpty { cellValue ?: "" }
                            if (v.isNotEmpty()) for (k in 0 until cellRepeat) sh?.put(row, col + k, v)
                            col += cellRepeat
                        }
                        "table:table-row" -> {
                            if (rowRepeat > 1) {
                                // repeated non-empty rows: copy them (empty filler rows are just skipped)
                                val s = sh
                                if (s != null) {
                                    val cells = (0..s.maxCol).mapNotNull { c -> s.cells[key(row, c)]?.let { c to it } }
                                    if (cells.isNotEmpty()) for (k in 1 until minOf(rowRepeat, 200)) cells.forEach { (c, v) -> s.put(row + k, c, v) }
                                }
                                row += rowRepeat - 1
                            }
                        }
                        "table:table" -> { sh?.let { out += it }; sh = null }
                    }
                }
            }
            out.ifEmpty { throw IllegalArgumentException("No sheets") }
        }
    }

    // ------------------------------------------------------------------ csv

    private fun readCsv(file: File, forced: Char?): Sheet {
        val bytes = file.readBytes().let { if (it.size > 8_000_000) it.copyOf(8_000_000) else it }
        var text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        // the delimiter used most in the first line (comma, semicolon or tab)
        val first = text.lineSequence().firstOrNull().orEmpty()
        val sep = forced ?: listOf(',', ';', '\t').maxByOrNull { d -> first.count { it == d } } ?: ','
        val sh = Sheet("")
        var row = 0
        var col = 0
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        text = text.replace("\r\n", "\n").replace('\r', '\n')
        while (i < text.length && row < MAX_ROWS) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                sep -> { sh.put(row, col, cell.toString()); cell.setLength(0); col++ }
                '\n' -> { sh.put(row, col, cell.toString()); cell.setLength(0); col = 0; row++ }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty()) sh.put(row, col, cell.toString())
        // column widths from the longest text (like Excel's auto-fit, capped)
        val longest = HashMap<Int, Int>()
        for ((k, v) in sh.cells) {
            val c = (k % 100_000L).toInt()
            longest[c] = maxOf(longest[c] ?: 0, v.lineSequence().maxOf { it.length })
        }
        longest.forEach { (c, n) -> sh.widths[c] = (n * 5.6f + 10f).coerceIn(40f, 260f) }
        return sh
    }
}
