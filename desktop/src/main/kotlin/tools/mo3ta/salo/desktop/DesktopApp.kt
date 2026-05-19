package tools.mo3ta.salo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import tools.mo3ta.salo.desktop.qr.QR_TTL_MS
import tools.mo3ta.salo.desktop.qr.buildSubmitPayload
import tools.mo3ta.salo.desktop.qr.currentRoundKey
import tools.mo3ta.salo.desktop.qr.renderQrBitmap

private val Background = Color(0xFF0F0F1A)
private val Panel = Color(0xFF16213E)
private val Gold = Color(0xFFE6B450)
private val Mute = Color(0xFFB8B8C8)

@Composable
fun DesktopApp() {
    val uid = remember { DeviceId.get() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            var count by remember { mutableStateOf(0) }
            var qr by remember { mutableStateOf<ImageBitmap?>(null) }
            var qrExpiresAt by remember { mutableStateOf(0L) }
            var sharedCount by remember { mutableStateOf(0) }
            var remainingMs by remember { mutableStateOf(0L) }

            LaunchedEffect(qrExpiresAt) {
                while (qrExpiresAt > 0L) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    remainingMs = (qrExpiresAt - now).coerceAtLeast(0L)
                    if (remainingMs <= 0L) {
                        qr = null
                        qrExpiresAt = 0L
                        break
                    }
                    delay(500)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = "صلِّ على النبي ﷺ",
                        color = Gold,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "اضغط للعدّ ثم أنشئ رمز QR لمسحه من التطبيق",
                        color = Mute,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(24.dp))

                    if (qr == null) {
                        CounterPanel(
                            count = count,
                            onTap = { count++ },
                            onReset = { count = 0 },
                            onGenerate = {
                                if (count > 0) {
                                    val payload = buildSubmitPayload(uid = uid, count = count)
                                    qr = renderQrBitmap(payload, size = 720)
                                    qrExpiresAt = Clock.System.now().toEpochMilliseconds() + QR_TTL_MS
                                    sharedCount = count
                                    count = 0
                                }
                            },
                        )
                    } else {
                        QrPanel(
                            qr = qr!!,
                            count = sharedCount,
                            remainingMs = remainingMs,
                            roundKey = currentRoundKey(Clock.System.now()),
                            onDone = {
                                qr = null
                                qrExpiresAt = 0L
                                sharedCount = 0
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CounterPanel(
    count: Int,
    onTap: () -> Unit,
    onReset: () -> Unit,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            color = Gold,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "صلاة",
            color = Mute,
            fontSize = 16.sp,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Background,
            ),
        ) {
            Text(
                text = "اللهم صلِّ على محمد",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onGenerate,
            enabled = count > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A2A4A),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF1E1E32),
                disabledContentColor = Mute.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = "إنشاء رمز QR للمشاركة",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onReset,
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("تصفير العدّاد", color = Mute)
        }
    }
}

@Composable
private fun QrPanel(
    qr: ImageBitmap,
    count: Int,
    remainingMs: Long,
    roundKey: String,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "امسح هذا الرمز من تطبيق صلِّ عليه",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "تم تصفير العدّاد. الرمز صالح لمرة واحدة فقط.",
            color = Mute,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = qr,
                contentDescription = "QR",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "العدد المُشارك: $count",
            color = Gold,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "جولة: $roundKey",
            color = Mute,
            fontSize = 13.sp,
        )
        val seconds = remainingMs / 1000L
        val mm = seconds / 60
        val ss = seconds % 60
        Text(
            text = "ينتهي خلال %02d:%02d".format(mm, ss),
            color = Mute,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = Background,
            ),
        ) {
            Text("تم — العودة للعدّاد", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
