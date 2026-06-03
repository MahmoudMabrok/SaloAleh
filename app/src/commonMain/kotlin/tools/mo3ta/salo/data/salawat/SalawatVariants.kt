package tools.mo3ta.salo.data.salawat

import org.jetbrains.compose.resources.StringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.salawat_variant_1
import tools.mo3ta.salo.generated.resources.salawat_variant_2
import tools.mo3ta.salo.generated.resources.salawat_variant_3
import tools.mo3ta.salo.generated.resources.salawat_variant_4
import tools.mo3ta.salo.generated.resources.salawat_variant_5
import tools.mo3ta.salo.generated.resources.salawat_variant_6

/** Single source of truth for the selectable salawat wordings (sky-tap flying text). */
object SalawatVariants {
    val textResIds: List<StringResource> = listOf(
        Res.string.salawat_variant_1,
        Res.string.salawat_variant_2,
        Res.string.salawat_variant_3,
        Res.string.salawat_variant_4,
        Res.string.salawat_variant_5,
        Res.string.salawat_variant_6,
    )

    val COUNT: Int get() = textResIds.size
}
