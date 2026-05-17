package tools.mo3ta.salo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.data.qr.QrVerifyResult
import tools.mo3ta.salo.data.qr.verifyExtensionQr
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.presentation.MohamedLoversViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.launchQrScanner
import tools.mo3ta.salo.ui.showPlatformToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionQrScreen(onBack: () -> Unit) {
    val sessionStore: MohamedLoversSessionStore = koinInject()
    val viewModel: MohamedLoversViewModel = koinViewModel()
    val analyticsManager: AnalyticsManager = koinInject()

    LaunchedEffect(Unit) {
        analyticsManager.logView("ExtensionQrScreen")
    }

    Scaffold(
        containerColor = Color(0xFF0f0f1a),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "استلام من الإضافة",
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF16213e),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "⚠️",
                fontSize = 48.sp,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "تحذير مهم",
                color = Color(0xFFFF6B6B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "استخدام رمز QR مزوّر أو غير صالح (محاولة غش) سيؤدي إلى تصفير جميع بياناتك بالكامل.",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "امسح فقط رمز QR الظاهر في إضافة المتصفح الخاصة بك.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    launchQrScanner { raw ->
                        if (raw == null) return@launchQrScanner
                        val nowMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val lastTs = sessionStore.getLastAppliedQrTs()
                        when (val result = verifyExtensionQr(raw, nowMs, lastTs)) {
                            is QrVerifyResult.Success -> {
                                viewModel.applyExtensionScore(result.round, result.count)
                                sessionStore.saveLastAppliedQrTs(nowMs)
                                showPlatformToast("تمت إضافة ${result.count} صلاة من الإضافة ✓")
                            }
                            is QrVerifyResult.Error -> {
                                viewModel.resetCurrentRoundScore()
                                showPlatformToast("⚠️ ${result.message} — تم تصفير نقاطك")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MohamedLoversPalette.GoldGlow,
                    contentColor = Color(0xFF0f0f1a),
                ),
            ) {
                Text(
                    text = "مسح رمز QR",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
