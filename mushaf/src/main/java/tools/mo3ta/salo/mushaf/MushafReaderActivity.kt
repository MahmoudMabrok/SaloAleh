package tools.mo3ta.salo.mushaf

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallHelper
import com.mushafimad.core.MushafLibrary
import com.mushafimad.ui.di.uiModule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mushafimad.ui.mushaf.MushafView
import kotlinx.coroutines.delay
import tools.mo3ta.salo.data.quran.PageDwellCounter
import tools.mo3ta.salo.ui.quran.MushafPageBridge

/**
 * On-demand reader. The Mushaf Imad pages and its Realm file ship in this
 * module, not in the base app, and the library copies that Realm into its own
 * files the first time this screen opens.
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
        val initialPage = intent.getIntExtra(EXTRA_PAGE, 0).takeIf { it in 1..604 }
        val alreadyCounted = intent.getIntArrayExtra(EXTRA_COUNTED)?.toSet().orEmpty()
        setContent {
            MushafReader(
                initialPage = initialPage,
                alreadyCounted = alreadyCounted,
                onClose = { finish() },
            )
        }
    }

    private fun startMushafLibrary(): Boolean = runCatching {
        SplitInstallHelper.loadLibrary(this, "realmc")
        if (!MushafLibrary.isInitialized()) {
            MushafLibrary.initialize(this)
            MushafLibrary.getKoin().loadModules(listOf(uiModule))
        }
    }.isSuccess

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
    onClose: () -> Unit,
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
    Box(Modifier.fillMaxSize()) {
        MushafView(
            initialPage = initialPage,
            showNavigationControls = false,
            onPageChanged = { next ->
                page = next
                counter.onPageRendered(next, SystemClock.elapsedRealtime())?.let { credited ->
                    MushafPageBridge.onPageQualified?.invoke(credited)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "إغلاق المصحف",
                tint = Color(0xFF1A4035),
            )
        }
    }
}
