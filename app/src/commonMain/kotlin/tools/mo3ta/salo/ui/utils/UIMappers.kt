package tools.mo3ta.salo.ui.utils


fun String.mapCountry(): String {
    return this.takeUnless { it == "IL"} ?: "PS"
}