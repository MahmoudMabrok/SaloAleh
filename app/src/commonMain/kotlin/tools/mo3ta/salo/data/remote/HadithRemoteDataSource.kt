package tools.mo3ta.salo.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import tools.mo3ta.salo.data.hadith.HadithItem
import tools.mo3ta.salo.data.media.MediaItem

internal class HadithRemoteDataSource(private val client: HttpClient) {

    suspend fun fetchHadiths(): List<HadithItem> =
        client.get(HadithApi.HADITHS_URL).body<List<HadithDto>>().map { it.toDomain() }

    suspend fun fetchMedia(): List<MediaItem> =
        client.get(HadithApi.MEDIA_URL).body<List<MediaDto>>().mapNotNull { it.toDomain() }
}
