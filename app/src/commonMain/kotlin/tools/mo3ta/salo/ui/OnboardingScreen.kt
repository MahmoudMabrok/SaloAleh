package tools.mo3ta.salo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.components.MohamedLoversSkyBackground

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = buildOnboardingPages()

    Box(modifier = Modifier.fillMaxSize()) {
        MohamedLoversSkyBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(52.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (page < pages.lastIndex) {
                    TextButton(onClick = onDone) {
                        Text(
                            text = stringResource(Res.string.onboarding_skip),
                            color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.6f))

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 12 }
                    val exit = fadeOut(tween(200))
                    enter togetherWith exit
                },
                label = "onboarding-page",
            ) { currentPage ->
                OnboardingPageContent(pages[currentPage])
            }

            Spacer(Modifier.weight(1f))

            PageDots(total = pages.size, current = page)

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { if (page < pages.lastIndex) page++ else onDone() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldBase,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = if (page < pages.lastIndex) stringResource(Res.string.onboarding_next) else stringResource(Res.string.onboarding_start),
                    color = MohamedLoversPalette.SkyTop,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPageData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        val glowColor = page.accentColor

        Box(contentAlignment = Alignment.Center) {
            Spacer(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.28f),
                                    Color.Transparent,
                                ),
                                radius = size.minDimension / 2f,
                            ),
                        )
                    },
            )
            Text(
                text = page.emoji,
                fontSize = 76.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = page.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MohamedLoversPalette.GoldGlow,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = page.subtitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MohamedLoversPalette.GoldWarm,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        if (page.body.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = page.body,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun PageDots(total: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val dotSize: Dp by animateDpAsState(
                targetValue = if (index == current) 10.dp else 6.dp,
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (index == current) MohamedLoversPalette.GoldHighlight
                        else MohamedLoversPalette.GoldHighlight.copy(alpha = 0.28f),
                    ),
            )
        }
    }
}

private data class OnboardingPageData(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val accentColor: Color,
)

@Composable
private fun buildOnboardingPages() = listOf(
    OnboardingPageData(
        emoji = "🌙",
        title = stringResource(Res.string.onboarding_welcome_title),
        subtitle = stringResource(Res.string.onboarding_welcome_subtitle),
        body = stringResource(Res.string.onboarding_welcome_body),
        accentColor = MohamedLoversPalette.GoldHighlight,
    ),
    OnboardingPageData(
        emoji = "⚡",
        title = stringResource(Res.string.onboarding_rounds_title),
        subtitle = stringResource(Res.string.onboarding_rounds_subtitle),
        body = stringResource(Res.string.onboarding_rounds_body),
        accentColor = MohamedLoversPalette.AtmosphereViolet,
    ),
    OnboardingPageData(
        emoji = "🥇",
        title = stringResource(Res.string.onboarding_achievements_title),
        subtitle = stringResource(Res.string.onboarding_achievements_subtitle),
        body = stringResource(Res.string.onboarding_achievements_body),
        accentColor = MohamedLoversPalette.GoldHighlight,
    ),
    OnboardingPageData(
        emoji = "🤲",
        title = stringResource(Res.string.onboarding_pray_title),
        subtitle = "«مَن صلَّى عليَّ صلاةً صلَّى اللهُ عليه بها عشرًا»",
        body = "",
        accentColor = MohamedLoversPalette.GoldHighlight,
    ),
)
