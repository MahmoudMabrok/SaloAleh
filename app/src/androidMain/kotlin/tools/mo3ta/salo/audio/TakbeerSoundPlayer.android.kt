package tools.mo3ta.salo.audio

import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import tools.mo3ta.salo.AndroidAppContext
import tools.mo3ta.salo.R
import kotlin.coroutines.resume

actual fun createTakbeerSoundPlayer(): TakbeerSoundPlayer = AndroidTakbeerSoundPlayer()

private class AndroidTakbeerSoundPlayer : TakbeerSoundPlayer {

    private var player: MediaPlayer? = null

    override suspend fun play() {
        stop()
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = MediaPlayer.create(AndroidAppContext.get(), R.raw.takbeer)
            if (mp == null) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            player = mp
            val finish: () -> Unit = {
                runCatching { mp.release() }
                if (player === mp) player = null
                if (cont.isActive) cont.resume(Unit)
            }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            cont.invokeOnCancellation {
                runCatching { mp.release() }
                if (player === mp) player = null
            }
            mp.start()
        }
    }

    override fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}
