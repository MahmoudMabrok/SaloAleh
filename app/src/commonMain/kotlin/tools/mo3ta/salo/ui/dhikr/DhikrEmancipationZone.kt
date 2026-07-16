package tools.mo3ta.salo.ui.dhikr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.dhikr_add
import tools.mo3ta.salo.generated.resources.dhikr_back_cd
import tools.mo3ta.salo.generated.resources.dhikr_gains_count_unit
import tools.mo3ta.salo.generated.resources.dhikr_gains_tap_hint
import tools.mo3ta.salo.generated.resources.dhikr_necks_freed_all
import tools.mo3ta.salo.generated.resources.dhikr_necks_freed_partial
import tools.mo3ta.salo.generated.resources.dhikr_phrase
import tools.mo3ta.salo.generated.resources.dhikr_rank_number
import tools.mo3ta.salo.generated.resources.dhikr_rank_subtitle
import tools.mo3ta.salo.generated.resources.dhikr_rank_unranked
import tools.mo3ta.salo.generated.resources.dhikr_view_gains
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.ui.ChallengeBubbleButton
import tools.mo3ta.salo.ui.ghars.arefRuqaaFamily
import tools.mo3ta.salo.ui.ghars.ibmPlexArabicFamily
import kotlin.random.Random

/**
 * "The emancipation engine" (عتق الرقاب) — an alternative hero for the 100-dhikr challenge that
 * rebuilds the counter out of the hadith's own gains. The sky is the progress bar (night → dawn),
 * a chain of ten shackles fills and snaps open — one freed soul per ten dhikr — and the tap count
 * lives underneath. Gated behind [DHIKR_GAINS_UI_ENABLED] in DhikrRewardsScreen.
 */

/** The hundred of the hadith. Ten shackles × ten dhikr — independent of the daily goal. */
private const val HUNDRED = 100
private const val DECADES = 10

private object EmancipationColors {
    val NightStops = arrayOf(
        0f to Color(0xFF02130C),
        0.58f to Color(0xFF06301F),
        1f to Color(0xFF0A3A26),
    )
    val DawnStops = arrayOf(
        0f to Color(0xFF123F2E),
        0.42f to Color(0xFF2E6B4E),
        0.72f to Color(0xFF7C9A5D),
        0.96f to Color(0xFFD9A84E),
    )
    val RingTrack = Color(0xFFD2E2D6).copy(alpha = 0.22f)
    val RingFill = Color(0xFF6FCF9E)
    val RingBroken = Color(0xFFE9C97F)
    val Soul = Color(0xFFF0EAD8)
    val Star = Color(0xFFCFE4D0)
}

private data class Star(val x: Float, val y: Float, val radius: Float, val alpha: Float)

/** who rises at each decade: a mix of men and children, matching the design study. */
private val FreedKinds = listOf(true, false, true, false, true, true, false, true, false, true)

@Composable
internal fun DhikrEmancipationZone(
    count: Int,
    rank: Int,
    participantCount: Int,
    canCount: Boolean,
    onTap: () -> Unit,
    onBack: () -> Unit,
    onRankClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onViewGains: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The chain and sky live inside the *current* hundred, so every hundred is a fresh
    // emancipation. A completed hundred (count is a positive multiple of 100) shows a full
    // chain rather than snapping back to an empty one.
    val withinHundred = if (count > 0 && count % HUNDRED == 0) HUNDRED else count % HUNDRED
    // The freed-necks tally is cumulative and unbounded — it keeps climbing past the first 100.
    val totalNecks = count / DECADES
    val currentNecks = withinHundred / DECADES
    val dawnAlpha by animateFloatAsState(
        targetValue = withinHundred.toFloat() / HUNDRED,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "dawn",
    )

    val stars = remember {
        val random = Random(0x5A10)
        List(42) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat() * 0.46f,
                radius = if (random.nextFloat() < 0.2f) 1.5f else 1f,
                alpha = 0.25f + random.nextFloat() * 0.55f,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(*EmancipationColors.NightStops))
            .clickable(
                enabled = canCount,
                onClickLabel = stringResource(Res.string.dhikr_add),
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        // Dawn rises over the night as the hundred approaches.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dawnAlpha }
                .background(Brush.verticalGradient(*EmancipationColors.DawnStops)),
        )
        // Stars fade out as dawn takes over.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - dawnAlpha },
        ) {
            stars.forEach { star ->
                drawCircle(
                    color = EmancipationColors.Star.copy(alpha = star.alpha),
                    radius = star.radius.dp.toPx(),
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = DhikrSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.dhikr_back_cd),
                        tint = Color.White.copy(alpha = 0.6f),
                    )
                }
                ChallengeBubbleButton(ChallengeType.DHIKR.id)
                Surface(
                    onClick = onRankClick,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    val chipText = if (rank > 0 && participantCount > 0) {
                        "${stringResource(Res.string.dhikr_rank_number, rank)} · ${stringResource(Res.string.dhikr_rank_subtitle, participantCount)}"
                    } else {
                        stringResource(Res.string.dhikr_rank_unranked)
                    }
                    Text(
                        text = chipText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontFamily = ibmPlexArabicFamily(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            DhikrManualEntryButton(
                onClick = onManualEntryClick,
                onDarkBackground = true,
            )

            Spacer(Modifier.weight(1f))

            FreedSoulsWire(freedNecks = currentNecks)

            FreedLabel(totalNecks = totalNecks, currentNecks = currentNecks)

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.dhikr_phrase),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 19.sp,
                fontFamily = arefRuqaaFamily(),
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(14.dp))

            ShackleChain(count = withinHundred)

            Spacer(Modifier.height(16.dp))

            Text(
                text = count.toString(),
                color = Color(0xFFF4F8F0),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = ibmPlexArabicFamily(),
                lineHeight = 58.sp,
            )
            Text(
                text = stringResource(Res.string.dhikr_gains_count_unit),
                color = Color(0xFFF4F8F0).copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = ibmPlexArabicFamily(),
            )

            Spacer(Modifier.weight(1f))

            ViewGainsButton(onClick = onViewGains)

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.dhikr_gains_tap_hint),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** "What you gain" — opens the غنائم اليوم spoils sheet on demand, before reaching the hundred. */
@Composable
private fun ViewGainsButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = EmancipationColors.RingBroken.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, EmancipationColors.RingBroken.copy(alpha = 0.55f)),
    ) {
        Text(
            text = stringResource(Res.string.dhikr_view_gains),
            color = EmancipationColors.RingBroken,
            fontSize = 13.sp,
            fontFamily = ibmPlexArabicFamily(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun FreedLabel(totalNecks: Int, currentNecks: Int) {
    val text = when {
        totalNecks <= 0 -> ""
        currentNecks >= DECADES -> stringResource(Res.string.dhikr_necks_freed_all)
        else -> stringResource(Res.string.dhikr_necks_freed_partial, totalNecks, DECADES - currentNecks)
    }
    Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                color = EmancipationColors.RingBroken,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ibmPlexArabicFamily(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FreedSoulsWire(freedNecks: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        // The horizon line the freed souls stand on.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.82f)
                .padding(bottom = 3.dp)
                .height(1.5.dp)
                .background(EmancipationColors.Soul.copy(alpha = 0.4f)),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(DECADES) { index ->
                AnimatedVisibility(
                    visible = index < freedNecks,
                    enter = scaleIn(tween(500, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
                    exit = scaleOut(tween(200)) + fadeOut(tween(200)),
                ) {
                    FreedSoul(isMan = FreedKinds[index % FreedKinds.size])
                }
            }
        }
    }
}

/** Faceless figure in a thobe, arms raised — a freed soul rising to the horizon. */
@Composable
private fun FreedSoul(isMan: Boolean) {
    val height = if (isMan) 30.dp else 22.dp
    val width = height * (26f / 34f)
    Canvas(modifier = Modifier.size(width = width, height = height)) {
        val sx = size.width / 26f
        val sy = size.height / 34f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)

        // Robe.
        val robe = Path().apply {
            moveTo(p(9.5f, 11.5f).x, p(9.5f, 11.5f).y)
            lineTo(p(16.5f, 11.5f).x, p(16.5f, 11.5f).y)
            cubicTo(
                p(18.2f, 18.5f).x, p(18.2f, 18.5f).y,
                p(19.3f, 26f).x, p(19.3f, 26f).y,
                p(19.8f, 33f).x, p(19.8f, 33f).y,
            )
            lineTo(p(6.2f, 33f).x, p(6.2f, 33f).y)
            cubicTo(
                p(6.7f, 26f).x, p(6.7f, 26f).y,
                p(7.8f, 18.5f).x, p(7.8f, 18.5f).y,
                p(9.5f, 11.5f).x, p(9.5f, 11.5f).y,
            )
            close()
        }
        drawPath(robe, color = EmancipationColors.Soul)

        // Head.
        drawCircle(
            color = EmancipationColors.Soul,
            radius = 4f * sx,
            center = p(13f, 5.5f),
        )

        // Arms (raised).
        val armStroke = Stroke(width = 2.6f * sx, cap = StrokeCap.Round)
        val armL = Path().apply {
            moveTo(p(10f, 13f).x, p(10f, 13f).y)
            cubicTo(
                p(7.5f, 11f).x, p(7.5f, 11f).y,
                p(5.2f, 8f).x, p(5.2f, 8f).y,
                p(4.2f, 4.8f).x, p(4.2f, 4.8f).y,
            )
        }
        val armR = Path().apply {
            moveTo(p(16f, 13f).x, p(16f, 13f).y)
            cubicTo(
                p(18.5f, 11f).x, p(18.5f, 11f).y,
                p(20.8f, 8f).x, p(20.8f, 8f).y,
                p(21.8f, 4.8f).x, p(21.8f, 4.8f).y,
            )
        }
        drawPath(armL, color = EmancipationColors.Soul, style = armStroke)
        drawPath(armR, color = EmancipationColors.Soul, style = armStroke)
    }
}

@Composable
private fun ShackleChain(count: Int) {
    // A gentle arc, mirroring the design study's marginTop offsets.
    val arc = listOf(16, 9, 4, 1, 0, 0, 1, 4, 9, 16)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        repeat(DECADES) { index ->
            val decadeStart = index * DECADES
            val broken = count >= decadeStart + DECADES
            val fillFraction = when {
                broken -> 1f
                count > decadeStart -> (count - decadeStart).toFloat() / DECADES
                else -> 0f
            }
            Shackle(
                fillFraction = fillFraction,
                broken = broken,
                modifier = Modifier.padding(top = arc[index].dp),
            )
        }
    }
}

@Composable
private fun Shackle(
    fillFraction: Float,
    broken: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedFill by animateFloatAsState(
        targetValue = fillFraction,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "shackleFill",
    )
    val rotation by animateFloatAsState(
        targetValue = if (broken) -42f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "shackleBreak",
    )
    val scale by animateFloatAsState(
        targetValue = if (broken) 1.06f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "shackleScale",
    )
    val fillColor = if (broken) EmancipationColors.RingBroken else EmancipationColors.RingFill

    Canvas(
        modifier = modifier
            .size(width = 26.dp, height = 30.dp)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            },
    ) {
        val stroke = 3.4.dp.toPx()
        val diameter = size.width - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val ringSize = Size(diameter, diameter)
        // Track.
        drawArc(
            color = EmancipationColors.RingTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = ringSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (broken) {
            // Snapped open: an arc with a visible gap.
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = 360f * 0.72f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        } else if (animatedFill > 0f) {
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedFill,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
