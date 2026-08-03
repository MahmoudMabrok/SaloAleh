package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

private const val VOICE_DHIKR_FORM_URL =
    "https://script.google.com/macros/s/AKfycby0iRCm_qYASYLppPhF9FUTHyEuiIsqxV-Zm_Rm0r7NLQ3DuVUshT9ZRV5vc8zgplbnKQ/exec"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDhikrScreen(
    onBack: () -> Unit,
) {
    val analyticsManager: AnalyticsManager = koinInject()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        analyticsManager.logView("VoiceDhikrScreen")
    }

    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.voice_dhikr_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MohamedLoversPalette.DeepBlue,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MohamedLoversPalette.GoldBase.copy(alpha = 0.15f))
                    .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MohamedLoversPalette.GoldHighlight,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.voice_dhikr_headline),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.voice_dhikr_intro),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MohamedLoversPalette.GoldBase.copy(alpha = 0.12f),
                                MohamedLoversPalette.GoldBase.copy(alpha = 0.04f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        MohamedLoversPalette.GoldGlow.copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.voice_dhikr_how_header),
                        color = MohamedLoversPalette.GoldGlow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    StepRow(number = "1", text = stringResource(Res.string.voice_dhikr_step_1))
                    StepRow(number = "2", text = stringResource(Res.string.voice_dhikr_step_2))
                    StepRow(number = "3", text = stringResource(Res.string.voice_dhikr_step_3))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.voice_dhikr_privacy),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MohamedLoversPalette.GoldHighlight)
                    .clickable {
                        analyticsManager.logAction(AppAnalytics.VOICE_DHIKR_FORM_OPENED)
                        uriHandler.openUri(VOICE_DHIKR_FORM_URL)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MohamedLoversPalette.DeepBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.voice_dhikr_start),
                        color = MohamedLoversPalette.DeepBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(Res.string.voice_dhikr_external_note),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
