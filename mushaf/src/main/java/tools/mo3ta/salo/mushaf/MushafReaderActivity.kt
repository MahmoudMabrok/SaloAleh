package tools.mo3ta.salo.mushaf

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.mushafimad.ui.mushaf.MushafWithPlayerView
import kotlinx.coroutines.delay
import tools.mo3ta.salo.data.quran.PageDwellCounter
import tools.mo3ta.salo.ui.quran.MushafPageBridge

/**
 * On-demand reader. The Mushaf Imad code and its Realm file ship in this
 * module, not in the base app, and the library copies that Realm into its own
 * files the first time this screen opens. The page images are in the
 * on-demand mushaf_assets asset pack ([MushafPackController]).
 *
 * Audio: uses [MushafWithPlayerView] (mushaf-ui README) so playback, reciter
 * pick, verse highlight, and streamed/cached download work in-process.
 */
class MushafReaderActivity : ComponentActivity() {
    private var pack: MushafPackController? = null

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
        // Page images are in the on-demand mushaf_assets pack: download it on
        // first open, then read it from the pack's folder.
        val controller = MushafPackController(this)
        pack = controller
        controller.start()
        setContent {
            if (controller.ui == PackUi.Ready) {
                MushafReader(
                    initialPage = initialPage,
                    alreadyCounted = alreadyCounted,
                )
            } else {
                MushafPackScreen(
                    ui = controller.ui,
                    onRetry = controller::retry,
                    onConfirm = controller::confirm,
                    onCancel = controller::cancel,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onDestroy() {
        pack?.release()
        super.onDestroy()
    }

    private fun startMushafLibrary(): Boolean = runCatching {
        SplitInstallHelper.loadLibrary(this, "realmc")
        startRealm()
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
    val context = LocalContext.current
    // Reading mode hides the player; listening mode shows it. The same
    // MushafWithPlayerView stays composed, so the current page is kept.
    var listening by rememberSaveable { mutableStateOf(MushafModePrefs.isListening(context)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFE6EFDC))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MushafWithPlayerView(
            initialPage = initialPage,
            showNavigationControls = false,
            showAudioPlayer = listening,
            onPageChanged = { next ->
                page = next
                counter.onPageRendered(next, SystemClock.elapsedRealtime())?.let { credited ->
                    MushafPageBridge.onPageQualified?.invoke(credited)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        MushafModeToggle(
            listening = listening,
            onToggle = {
                // Leaving listening mode hides the player; mushaf-ui 0.4.1 then stops
                // the recitation and clears the verse highlight. The page is kept.
                val next = !listening
                listening = next
                MushafModePrefs.setListening(context, next)
            },
            modifier = Modifier
                // Centre of the header row: surah name and hizb/part sit at the sides.
                .align(Alignment.TopCenter)
                .padding(top = 0.dp),
        )
    }
}

@Composable
private fun MushafModeToggle(
    listening: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The icon shows the mode you switch TO.
    val action = stringResource(
        if (listening) R.string.mushaf_switch_to_reading else R.string.mushaf_switch_to_listening,
    )
    val state = stringResource(
        if (listening) R.string.mushaf_mode_listening else R.string.mushaf_mode_reading,
    )
    SmallFloatingActionButton(
        onClick = onToggle,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = modifier.semantics {
            contentDescription = action
            stateDescription = state
        },
    ) {
        Text(text = if (listening) "📖" else "🎧", fontSize = 20.sp)
    }
}

/** Last chosen Mushaf mode (reading / listening), kept across openings. */
internal object MushafModePrefs {
    private const val FILE = "mushaf_reader_prefs"
    private const val KEY_LISTENING = "listening_mode"

    fun isListening(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LISTENING, true)

    fun setListening(context: Context, listening: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LISTENING, listening).apply()
    }
}
