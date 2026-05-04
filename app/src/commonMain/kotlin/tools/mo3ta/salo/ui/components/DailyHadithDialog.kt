package tools.mo3ta.salo.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.koin.compose.koinInject
import tools.mo3ta.salo.data.hadith.DailyHadithStore
import tools.mo3ta.salo.data.hadith.HadithItem

private val Gold = Color(0xFFD4AF37)
private val DeepNavy = Color(0xFF09142B)
private val MidNavy = Color(0xFF0F2040)
private val Cream = Color(0xFFFFF8E7)
private val MutedGold = Color(0xFFA89020)

@Composable
fun DailyHadithDialog(
    onDismiss: () -> Unit,
    store: DailyHadithStore = koinInject(),
) {
    var displayIndex by remember { mutableStateOf(store.currentIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = displayIndex,
                animationSpec = tween(durationMillis = 280),
                label = "hadith_crossfade",
            ) { idx ->
                HadithCard(
                    item = DailyHadithStore.HADITHS[idx],
                    index = idx,
                    total = DailyHadithStore.HADITHS.size,
                    onClose = {
                        store.currentIndex = (idx + 1) % DailyHadithStore.HADITHS.size
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun HadithCard(
    item: HadithItem,
    index: Int,
    total: Int,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(MidNavy, DeepNavy)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Gold.copy(alpha = 0.7f), Gold.copy(alpha = 0.2f))),
                shape = shape,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Header ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "✦  ❖  ✦",
                color = Gold,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "فضل الصلاة على النبي ﷺ",
                color = Gold.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            GoldGradientDivider()
            Spacer(Modifier.height(10.dp))

            // Progress bar
            val progress = (index + 1).toFloat() / total
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Gold.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Gold.copy(0.5f), Gold)),
                        ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${index + 1} / $total",
                color = MutedGold.copy(alpha = 0.8f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }

        // ── Scrollable body ─────────────────────────────────────────
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = item.title,
                color = Gold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = item.text,
                color = Cream,
                fontSize = 14.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.source,
                color = MutedGold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Footer ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoldGradientDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                text = "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى سَيِّدِنَا مُحَمَّدٍ",
                color = Gold.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = DeepNavy,
                ),
            ) {
                Text(text = "حسنًا", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GoldGradientDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Gold.copy(alpha = 0.45f), Color.Transparent),
                ),
            ),
    )
}
