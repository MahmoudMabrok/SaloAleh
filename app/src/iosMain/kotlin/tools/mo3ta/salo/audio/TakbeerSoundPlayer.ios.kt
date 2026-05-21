package tools.mo3ta.salo.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle

actual fun createTakbeerSoundPlayer(): TakbeerSoundPlayer = IosTakbeerSoundPlayer()

@OptIn(ExperimentalForeignApi::class)
private class IosTakbeerSoundPlayer : TakbeerSoundPlayer {

    private var player: AVAudioPlayer? = null

    override suspend fun play() {
        stop()
        val url = NSBundle.mainBundle.URLForResource("takbeer", withExtension = "mp3") ?: return
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        }
        val p = AVAudioPlayer(contentsOfURL = url, error = null)
        player = p
        p.play()
        try {
            while (p.isPlaying()) {
                delay(150)
            }
        } finally {
            p.stop()
            if (player === p) player = null
        }
    }

    override fun stop() {
        player?.stop()
        player = null
    }
}
