package tools.mo3ta.salo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.math.roundToInt

private const val FLIGHT_DURATION_MS = 2400
private const val REWARD_DURATION_MS = 8000
private const val REWARD_RISE_DP = 280f
private const val RIPPLE_DURATION_MS = 600
private const val EMIT_INTERVAL_MS = 700L
private const val REWARD_ID_OFFSET = 100_000_000L

private val FlightEasing = CubicBezierEasing(0.42f, 0f, 0.2f, 1f)
private val RewardEasing = CubicBezierEasing(0.22f, 0.7f, 0.25f, 1f)

@Composable
internal fun MohamedLoversPrayerOverlay(
    archCenter: Offset?,
    enabled: Boolean,
    prayerText: String,
    rewardText: String,
    blockedMessage: String,
    onBlessing: () -> Unit,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val prayers = remember { mutableStateListOf<FlyingPrayer>() }
    val rewards = remember { mutableStateListOf<RisingReward>() }
    val ripples = remember { mutableStateListOf<RippleMark>() }
    val idCounter = remember { longArrayOf(0L) }
    val lastEmitAt = remember { longArrayOf(0L) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            .pointerInput(enabled, archCenter) {
                detectTapGestures { tapPoint ->
                    onTap()
                    val id = idCounter[0]++
                    val windowTap = Offset(tapPoint.x + overlayOrigin.x, tapPoint.y + overlayOrigin.y)
                    ripples += RippleMark(id = id, at = windowTap)

                    val now = Clock.System.now().toEpochMilliseconds()
                    val scheduledAt = max(now, lastEmitAt[0] + EMIT_INTERVAL_MS)
                    lastEmitAt[0] = scheduledAt
                    val waitMs = scheduledAt - now

                    scope.launch {
                        if (waitMs > 0) delay(waitMs)
                        if (enabled && archCenter != null) {
                            prayers += FlyingPrayer(id = id, start = windowTap, end = archCenter)
                        } else if (blockedMessage.isNotBlank()) {
                            rewards += RisingReward(id = id, at = windowTap, text = blockedMessage)
                        }
                    }
                }
            },
    ) {
        ripples.forEach { mark ->
            key(mark.id) {
                RippleView(
                    origin = mark.at - overlayOrigin,
                    onEnd = { ripples.removeAll { it.id == mark.id } },
                )
            }
        }

        prayers.forEach { p ->
            key(p.id) {
                FlyingPrayerView(
                    start = p.start - overlayOrigin,
                    end = p.end - overlayOrigin,
                    text = prayerText,
                    onArrive = {
                        onBlessing()
                        rewards += RisingReward(id = p.id + REWARD_ID_OFFSET, at = p.end, text = rewardText)
                        prayers.removeAll { it.id == p.id }
                    },
                )
            }
        }

        rewards.forEach { r ->
            key(r.id) {
                RisingRewardView(
                    origin = r.at - overlayOrigin,
                    text = r.text,
                    onEnd = { rewards.removeAll { it.id == r.id } },
                )
            }
        }
    }
}

@Composable
private fun RippleView(origin: Offset, onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(RIPPLE_DURATION_MS))
        onEnd()
    }
    val density = LocalDensity.current
    val baseRadius = with(density) { 18.dp.toPx() }
    val maxRadius = with(density) { 40.dp.toPx() }
    val radius = baseRadius + (maxRadius - baseRadius) * progress.value
    val alphaValue = 1f - progress.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f * alphaValue),
            radius = radius,
            center = origin,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun FlyingPrayerView(start: Offset, end: Offset, text: String, onArrive: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(FLIGHT_DURATION_MS, easing = FlightEasing))
        onArrive()
    }
    val x = start.x + (end.x - start.x) * t.value
    val y = start.y + (end.y - start.y) * t.value
    val alphaAnim = when {
        t.value < 0.15f -> t.value / 0.15f
        t.value > 0.85f -> (1f - t.value) / 0.15f
        else -> 1f
    }.coerceIn(0f, 1f)
    val scaleAnim = 1f - t.value * 0.35f

    val density = LocalDensity.current
    Text(
        text = text,
        style = TextStyle(fontFamily = MohamedLoversFonts.arabic, fontWeight = FontWeight.W700, fontSize = 15.sp),
        color = MohamedLoversPalette.GoldGlow,
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - with(density) { 60.dp.toPx() }).roundToInt(),
                    (y - with(density) { 10.dp.toPx() }).roundToInt(),
                )
            }
            .alpha(alphaAnim)
            .scale(scaleAnim),
    )
}

@Composable
private fun RisingRewardView(origin: Offset, text: String, onEnd: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(REWARD_DURATION_MS, easing = RewardEasing))
        onEnd()
    }
    val alphaAnim = when {
        t.value < 0.06f -> t.value / 0.06f
        t.value > 0.85f -> (1f - t.value) / 0.15f
        else -> 1f
    }.coerceIn(0f, 1f)

    val density = LocalDensity.current
    val dy = with(density) { -(REWARD_RISE_DP.dp.toPx()) * t.value }
    Text(
        text = text,
        style = TextStyle(fontFamily = MohamedLoversFonts.arabic, fontWeight = FontWeight.W700, fontSize = 17.sp),
        color = MohamedLoversPalette.GoldWarm,
        modifier = Modifier
            .offset {
                IntOffset(
                    (origin.x - with(density) { 70.dp.toPx() }).roundToInt(),
                    (origin.y + dy - with(density) { 12.dp.toPx() }).roundToInt(),
                )
            }
            .alpha(alphaAnim),
    )
}

private data class FlyingPrayer(val id: Long, val start: Offset, val end: Offset)
private data class RisingReward(val id: Long, val at: Offset, val text: String)
private data class RippleMark(val id: Long, val at: Offset)
