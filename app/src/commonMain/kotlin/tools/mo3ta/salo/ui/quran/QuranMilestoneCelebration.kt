package tools.mo3ta.salo.ui.quran

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.quran_milestone_subtitle
import tools.mo3ta.salo.generated.resources.quran_milestone_title
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ParticleColors = listOf(
    Color(0xFF2ECC71),
    Color(0xFF1ABC9C),
    Color(0xFF87CEEB),
    Color(0xFF3EAA82),
    Color(0xFFFFFFFF),
    Color(0xFF78C4A0),
)

private data class Particle(val angle: Float, val speed: Float, val color: Color, val size: Float)

@Composable
fun QuranMilestoneCelebration(
    milestone: Int,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var animVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            animVisible = true
            delay(2500)
            animVisible = false
            delay(400)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = animVisible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(400)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        animVisible = false
                        onDismiss()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            ConfettiLayer()

            AnimatedVisibility(
                visible = animVisible,
                enter = scaleIn(tween(350, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                exit = scaleOut(tween(300)) + fadeOut(tween(300)),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF1B3A2E).copy(alpha = 0.95f),
                    shadowElevation = 24.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "📖",
                            fontSize = 48.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(Res.string.quran_milestone_subtitle),
                            color = Color(0xFF3EAA82),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.quran_milestone_title, milestone),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfettiLayer(modifier: Modifier = Modifier) {
    val particles = remember {
        List(40) { i ->
            Particle(
                angle = (i * 9f) * (PI / 180f).toFloat(),
                speed = 0.3f + (i % 5) * 0.15f,
                color = ParticleColors[i % ParticleColors.size],
                size = 6f + (i % 4) * 3f,
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confetti_progress",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = minOf(size.width, size.height) * 0.55f

        particles.forEach { p ->
            val t = (progress + p.angle / (2f * PI.toFloat())) % 1f
            val radius = t * maxRadius
            val alpha = (1f - t).coerceIn(0f, 1f)
            val x = cx + cos(p.angle) * radius
            val y = cy + sin(p.angle) * radius
            drawCircle(
                color = p.color.copy(alpha = alpha * 0.85f),
                radius = p.size,
                center = Offset(x, y),
            )
        }
    }
}
