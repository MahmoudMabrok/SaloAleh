package tools.mo3ta.salo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tools.mo3ta.salo.data.hadith.HadithItem
import tools.mo3ta.salo.data.media.MediaItem
import tools.mo3ta.salo.data.media.MediaType

@Serializable
internal data class HadithResponseDto(
    @SerialName("الأحاديث") val hadiths: List<HadithDto>,
)

@Serializable
internal data class HadithDto(
    @SerialName("العنوان") val title: String,
    @SerialName("النص") val text: String,
    @SerialName("المصدر") val source: String,
)

@Serializable
internal data class MediaResponseDto(
    val items: List<MediaDto>,
)

@Serializable
internal data class MediaDto(
    val title: String,
    val url: String,
    val type: String,
    val language: String,
)

internal fun HadithDto.toDomain(): HadithItem = HadithItem(
    title = title,
    text = text,
    source = source,
)

internal fun MediaDto.toDomain(): MediaItem? {
    val parsedType = runCatching { MediaType.valueOf(type.uppercase()) }.getOrNull() ?: return null
    return MediaItem(
        title = title,
        url = url,
        type = parsedType,
        language = language,
    )
}
