package tools.mo3ta.salo.data.quran

/** A Madani mushaf page number, accepting Latin and Arabic-Indic digits. */
fun parseMushafPage(raw: String): Int? {
    val digits = raw.trim().map { ch ->
        when (ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            in '۰'..'۹' -> '0' + (ch - '۰')
            else -> ch
        }
    }.joinToString("")
    return digits.toIntOrNull()?.takeIf { it in 1..604 }
}
