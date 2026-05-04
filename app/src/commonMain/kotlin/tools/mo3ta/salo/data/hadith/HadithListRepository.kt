package tools.mo3ta.salo.data.hadith

import kotlinx.coroutines.CancellationException
import tools.mo3ta.salo.data.media.MediaItem
import tools.mo3ta.salo.data.remote.HadithRemoteDataSource

class HadithListRepository(
    private val remote: HadithRemoteDataSource,
) {
    suspend fun loadHadiths(): Result<List<HadithItem>> = safeCall { remote.fetchHadiths() }

    suspend fun loadMedia(): Result<List<MediaItem>> = safeCall { remote.fetchMedia() }

    private inline fun <T> safeCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
