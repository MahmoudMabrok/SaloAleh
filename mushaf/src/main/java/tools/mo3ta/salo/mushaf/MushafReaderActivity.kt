package tools.mo3ta.salo.mushaf

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallHelper
import com.mushafimad.core.MushafLibrary
import com.mushafimad.ui.di.uiModule
import com.mushafimad.ui.mushaf.AssetLineImageSource
import com.mushafimad.ui.mushaf.MushafLineImages
import com.mushafimad.ui.mushaf.MushafWithPlayerView
import kotlinx.coroutines.delay
import tools.mo3ta.salo.data.quran.PageDwellCounter
import tools.mo3ta.salo.ui.quran.MushafPageBridge

/**
 * On-demand reader. The Mushaf Imad pages and its Realm file ship in this
 * module, not in the base app, and the library copies that Realm into its own
 * files the first time this screen opens.
 *
 * Audio: uses [MushafWithPlayerView] (mushaf-ui README) so playback, reciter
 * pick, verse highlight, and streamed/cached download work in-process.
 */
class MushafReaderActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Only this screen, after the user opens it. Both calls can throw Error.
        runCatching { SplitCompat.install(this) }
        runCatching { SplitCompat.installActivity(this) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!startMushafLibrary()) {
            finish()
            return
        }
        enableEdgeToEdge()
        val initialPage = intent.getIntExtra(EXTRA_PAGE, 0).takeIf { it in 1..604 }
        val alreadyCounted = intent.getIntArrayExtra(EXTRA_COUNTED)?.toSet().orEmpty()
        setContent {
            MushafReader(
                initialPage = initialPage,
                alreadyCounted = alreadyCounted,
            )
        }
    }

    private fun startMushafLibrary(): Boolean = runCatching {
        SplitInstallHelper.loadLibrary(this, "realmc")
        startRealm()
        // Page images come from the install-time :mushaf-assets pack, which Play
        // merges into the app's AssetManager (mushaf-ui 0.4.0 no longer bundles them).
        MushafLineImages.source = AssetLineImageSource()
        if (!MushafLibrary.isInitialized()) {
            MushafLibrary.initialize(this)
            MushafLibrary.getKoin().loadModules(listOf(uiModule))
        }
    }.isSuccess

    // The startup initializer is removed from the manifest so the base app can
    // open. Realm's native library is only in this module.
    private fun startRealm() {
        val initializer = Class.forName("io.realm.kotlin.internal.RealmInitializer")
            .getDeclaredConstructor()
            .newInstance()
        initializer.javaClass.getMethod("create", Context::class.java).invoke(initializer, this)
    }

    companion object {
        const val EXTRA_PAGE = "tools.mo3ta.salo.mushaf.PAGE"
        const val EXTRA_COUNTED = "tools.mo3ta.salo.mushaf.COUNTED"
        const val CLASS_NAME = "tools.mo3ta.salo.mushaf.MushafReaderActivity"
    }
}

@Composable
private fun MushafReader(
    initialPage: Int?,
    alreadyCounted: Set<Int>,
) {
    val counter = remember { PageDwellCounter(alreadyCounted) }
    var page by remember { mutableIntStateOf(initialPage ?: 0) }
    DisposableEffect(counter) {
        onDispose {
            counter.flush(SystemClock.elapsedRealtime())?.let { credited ->
                MushafPageBridge.onPageQualified?.invoke(credited)
            }
        }
    }
    LaunchedEffect(counter) {
        while (true) {
            delay(1_000)
            val current = page
            if (current in 1..604) {
                counter.onPageRendered(current, SystemClock.elapsedRealtime())?.let { credited ->
                    MushafPageBridge.onPageQualified?.invoke(credited)
                }
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFE6EFDC))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MushafWithPlayerView(
            initialPage = initialPage,
            showNavigationControls = false,
            showAudioPlayer = true,
            onPageChanged = { next ->
                page = next
                counter.onPageRendered(next, SystemClock.elapsedRealtime())?.let { credited ->
                    MushafPageBridge.onPageQualified?.invoke(credited)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
