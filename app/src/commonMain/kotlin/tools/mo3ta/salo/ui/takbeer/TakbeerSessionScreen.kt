package tools.mo3ta.salo.ui.takbeer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.presentation.TakbeerPhase
import tools.mo3ta.salo.presentation.TakbeerSessionUiState
import tools.mo3ta.salo.presentation.TakbeerSessionViewModel
import tools.mo3ta.salo.ui.tendays.TenDaysPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val UserAccent = Color(0xFF38BDF8)
private val UserAccentLight = Color(0xFF7DD3FC)
private val StopRed = Color(0xFFE53935)

@Composable
fun TakbeerSessionScreen(
    onBack: () -> Unit = {},
    viewModel: TakbeerSessionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsManager: AnalyticsManager = koinInject()

    LaunchedEffect(Unit) {
        analyticsManager.logView("TakbeerSessionScreen")
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopSession() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TenDaysPalette.BackgroundDark,
                        TenDaysPalette.BackgroundMid,
                        TenDaysPalette.BackgroundLight,
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.takbeer_back_cd),
                    tint = TenDaysPalette.TextPrimary,
                )
            }

            Header()
            Spacer(Modifier.height(18.dp))

            when (state.phase) {
                TakbeerPhase.SETUP -> SetupContent(state, viewModel)
                TakbeerPhase.RUNNING -> RunningContent(state, viewModel)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.takbeer_session_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.takbeer_session_subtitle),
            fontSize = 13.sp,
            color = TenDaysPalette.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupContent(
    state: TakbeerSessionUiState,
    viewModel: TakbeerSessionViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TenDaysPalette.CardBackgroundAlpha)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.takbeer_people_count_label),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TenDaysPalette.TextPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.takbeer_people_count_hint),
                fontSize = 12.sp,
                color = TenDaysPalette.TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepperButton(glyph = "−", onClick = { viewModel.onStep(-1) })
                OutlinedTextField(
                    value = state.peopleCountInput,
                    onValueChange = viewModel::onPeopleInputChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.width(96.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TenDaysPalette.TextPrimary,
                        unfocusedTextColor = TenDaysPalette.TextPrimary,
                        focusedBorderColor = TenDaysPalette.Gold,
                        unfocusedBorderColor = TenDaysPalette.GrayBorder,
                        cursorColor = TenDaysPalette.Gold,
                        focusedContainerColor = TenDaysPalette.SurfaceDark,
                        unfocusedContainerColor = TenDaysPalette.SurfaceDark,
                    ),
                )
                StepperButton(glyph = "+", onClick = { viewModel.onStep(1) })
            }
        }

        Spacer(Modifier.height(16.dp))

        val analyticsManager: AnalyticsManager = koinInject()
        Button(
            onClick = {
                analyticsManager.logAction("takbeer_session_start", mapOf("people" to state.peopleCountInput))
                viewModel.startSession()
            },
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = TenDaysPalette.Gold,
                disabledContainerColor = TenDaysPalette.Gold.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(Res.string.takbeer_start_session), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(18.dp))

        HowItWorksCard()
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(TenDaysPalette.Gold.copy(alpha = 0.18f))
            .border(1.dp, TenDaysPalette.Gold.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = TenDaysPalette.Gold, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HowItWorksCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TenDaysPalette.CardBackgroundAlpha)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.takbeer_how_it_works_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        listOf(
            stringResource(Res.string.takbeer_how_step_1),
            stringResource(Res.string.takbeer_how_step_2),
            stringResource(Res.string.takbeer_how_step_3),
            stringResource(Res.string.takbeer_how_step_4),
        ).forEach { line ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•", color = TenDaysPalette.Gold, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line,
                    fontSize = 13.sp,
                    color = TenDaysPalette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RunningContent(
    state: TakbeerSessionUiState,
    viewModel: TakbeerSessionViewModel,
) {
    val analyticsManager: AnalyticsManager = koinInject()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundBanner(state.roundsCompleted + 1)
        Spacer(Modifier.height(16.dp))

        PeopleRing(state)

        Spacer(Modifier.height(18.dp))

        if (state.isUserTurn) {
            Text(
                text = stringResource(Res.string.takbeer_your_turn_instruction),
                fontSize = 13.sp,
                color = UserAccentLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    analyticsManager.logAction("takbeer_user_done", mapOf("round" to "${state.roundsCompleted + 1}"))
                    viewModel.onUserDone()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UserAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.takbeer_done_next_turn),
                    color = Color(0xFF06283D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.takbeer_listen_wait),
                fontSize = 13.sp,
                color = TenDaysPalette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                analyticsManager.logAction("takbeer_session_stop", mapOf("rounds_completed" to "${state.roundsCompleted}"))
                viewModel.stopSession()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = StopRed),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(Res.string.takbeer_stop_session), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundBanner(round: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TenDaysPalette.Gold.copy(alpha = 0.14f))
            .border(1.dp, TenDaysPalette.Gold.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(Res.string.takbeer_round_number, round),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TenDaysPalette.Gold,
        )
    }
}

@Composable
private fun PeopleRing(state: TakbeerSessionUiState) {
    val pulseTransition = rememberInfiniteTransition()
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(640, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val nodeSize = 58.dp
        val ringRadius = (maxWidth - nodeSize) / 2 - 4.dp
        val count = state.peopleCount

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = TenDaysPalette.GrayBorder.copy(alpha = 0.4f),
                radius = ringRadius.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        for (i in 0 until count) {
            val angleRad = (-90.0 + i * 360.0 / count) * PI / 180.0
            val dx = ringRadius * cos(angleRad).toFloat()
            val dy = ringRadius * sin(angleRad).toFloat()
            val isUser = i == state.userIndex
            val isActive = i == state.currentTurn
            PersonNode(
                label = if (isUser) "U" else (i + 1).toString(),
                isUser = isUser,
                isActive = isActive,
                isDone = !isUser && i < state.currentTurn,
                scale = if (isActive) pulse else 1f,
                modifier = Modifier
                    .size(nodeSize)
                    .absoluteOffset(x = dx, y = dy),
            )
        }

        RingCenter(state = state, pulse = pulse)
    }
}

@Composable
private fun PersonNode(
    label: String,
    isUser: Boolean,
    isActive: Boolean,
    isDone: Boolean,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isUser && isActive -> UserAccent
        isActive -> TenDaysPalette.Gold
        isDone -> TenDaysPalette.Green.copy(alpha = 0.18f)
        else -> TenDaysPalette.SurfaceDark
    }
    val borderColor = when {
        isUser && isActive -> UserAccentLight
        isUser -> UserAccent
        isActive -> TenDaysPalette.Gold
        isDone -> TenDaysPalette.Green
        else -> TenDaysPalette.GrayBorder
    }
    val textColor = when {
        isUser && isActive -> Color.White
        isUser -> UserAccent
        isActive -> Color.Black
        isDone -> TenDaysPalette.Green
        else -> TenDaysPalette.TextSecondary
    }
    val animBg by animateColorAsState(bg)
    val animBorder by animateColorAsState(borderColor)

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(animBg)
            .border(2.dp, animBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (isUser) 20.sp else 17.sp,
        )
    }
}

@Composable
private fun RingCenter(state: TakbeerSessionUiState, pulse: Float) {
    Box(contentAlignment = Alignment.Center) {
        if (state.isUserTurn) {
            Text(
                text = stringResource(Res.string.takbeer_your_turn),
                color = UserAccentLight,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.scale(pulse),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.takbeer_label), color = TenDaysPalette.Gold, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.takbeer_participant_number, state.currentTurn + 1),
                    color = TenDaysPalette.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
