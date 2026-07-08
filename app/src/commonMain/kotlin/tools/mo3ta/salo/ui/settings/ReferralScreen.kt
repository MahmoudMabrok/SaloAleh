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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseApi
import tools.mo3ta.salo.data.referral.ReferralStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.copyToClipboard
import tools.mo3ta.salo.ui.getStoreUrl
import tools.mo3ta.salo.ui.shareText
import tools.mo3ta.salo.ui.showPlatformToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(
    onBack: () -> Unit,
) {
    val sessionStore: MohamedLoversSessionStore = koinInject()
    val referralStore: ReferralStore = koinInject()
    val firebaseApi: MohamedLoversFirebaseApi = koinInject()

    val uid = remember { sessionStore.getOrCreateUid() }
    val referralCode = remember { referralStore.getOrCreateReferralCode(uid) }
    var referralCount by remember { mutableStateOf(0) }
    var referralsTotal by remember { mutableStateOf(0L) }

    LaunchedEffect(uid) {
        firebaseApi.writeReferralCode(uid, referralCode)
        firebaseApi.fetchReferralCount(uid).onSuccess { referralCount = it }
        firebaseApi.fetchReferralsAllTimeTotal(uid).onSuccess { referralsTotal = it }
    }

    val shareBody = stringResource(Res.string.referral_share_body)
    val copiedToast = stringResource(Res.string.referral_code_copied)

    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.referral_screen_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
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

            Text(
                text = stringResource(Res.string.referral_reward_title),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.referral_reward_subtitle),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MohamedLoversPalette.GoldBase.copy(alpha = 0.15f),
                                MohamedLoversPalette.GoldBase.copy(alpha = 0.05f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        MohamedLoversPalette.GoldGlow.copy(alpha = 0.3f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(20.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.referral_your_code),
                        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = referralCode,
                            color = MohamedLoversPalette.GoldHighlight,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                        )

                        IconButton(
                            onClick = {
                                copyToClipboard(referralCode)
                                showPlatformToast(copiedToast)
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MohamedLoversPalette.GoldGlow,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = stringResource(Res.string.referral_stat_users),
                    value = referralCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(Res.string.referral_stat_total_salawat),
                    value = referralsTotal.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.referral_ajr_note),
                color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MohamedLoversPalette.GoldHighlight)
                    .clickable {
                        val link = "saloaleh://refer?code=$referralCode"
                        val storeUrl = getStoreUrl()
                        shareText("$shareBody\n$storeUrl\n$link")
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MohamedLoversPalette.DeepBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.referral_share_button),
                        color = MohamedLoversPalette.DeepBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp),
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
