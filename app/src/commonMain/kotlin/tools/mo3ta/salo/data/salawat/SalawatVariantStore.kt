package tools.mo3ta.salo.data.salawat

import com.russhwolf.settings.Settings

/**
 * Persists the salawat wording the user selected to fly up when tapping the sky.
 * Stored as an index into [SalawatVariants.textResIds]. Defaults to the first variant.
 */
class SalawatVariantStore(private val settings: Settings) {
    var variantIndex: Int
        get() = settings.getInt(KEY_VARIANT, DEFAULT_VARIANT)
            .coerceIn(0, SalawatVariants.COUNT - 1)
        set(v) = settings.putInt(KEY_VARIANT, v.coerceIn(0, SalawatVariants.COUNT - 1))

    private companion object {
        const val KEY_VARIANT = "salawat_variant_index"
        const val DEFAULT_VARIANT = 0
    }
}
