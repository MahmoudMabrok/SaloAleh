package tools.mo3ta.salo.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tools.mo3ta.dhikrmodel.DhikrModelRunner
import tools.mo3ta.dhikrmodel.DhikrRuntimeStatus
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.ui.components.MohamedLoversPalette

actual val dhikrModelTestAvailable: Boolean = true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun DhikrModelTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val runner = remember { DhikrModelRunner(context) }
    val state by runner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var phraseQuery by remember { mutableStateOf("007") }
    var installedSummary by remember { mutableStateOf("") }
    var startAfterPermission by remember { mutableStateOf(false) }
    val noBundlesText = stringResource(Res.string.dhikr_model_no_bundles)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && startAfterPermission) {
            scope.launch { runCatching { runner.startListening(phraseQuery) } }
        }
        startAfterPermission = false
    }

    LaunchedEffect(runner) {
        val installed = runner.availablePhrases()
        installedSummary = if (installed.isEmpty()) {
            noBundlesText
        } else {
            installed.joinToString { "${it.id} · ${it.text}" }
        }
        installed.firstOrNull()?.let { phraseQuery = it.id }
    }
    DisposableEffect(runner) {
        onDispose { runner.close() }
    }

    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.dhikr_model_test_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213E)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(Res.string.dhikr_model_test_intro),
                color = Color.White.copy(alpha = 0.75f),
                lineHeight = 22.sp,
            )

            OutlinedTextField(
                value = phraseQuery,
                onValueChange = { phraseQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.dhikr_model_phrase_label)) },
                supportingText = {
                    Text(stringResource(Res.string.dhikr_model_phrase_hint))
                },
                singleLine = true,
            )

            Text(
                text = installedSummary,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { scope.launch { runCatching { runner.load(phraseQuery) } } },
                    enabled = state.status != DhikrRuntimeStatus.LOADING &&
                        state.status != DhikrRuntimeStatus.LISTENING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MohamedLoversPalette.GoldGlow),
                ) {
                    Text(
                        stringResource(Res.string.dhikr_model_load),
                        color = MohamedLoversPalette.DeepBlue,
                    )
                }

                Button(
                    onClick = {
                        if (state.status == DhikrRuntimeStatus.LISTENING) {
                            scope.launch { runner.stopListening() }
                        } else if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            scope.launch { runCatching { runner.startListening(phraseQuery) } }
                        } else {
                            startAfterPermission = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = state.status != DhikrRuntimeStatus.LOADING,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.status == DhikrRuntimeStatus.LISTENING) {
                            Color(0xFFB23A48)
                        } else {
                            Color(0xFF2A9D8F)
                        },
                    ),
                ) {
                    Text(
                        stringResource(
                            if (state.status == DhikrRuntimeStatus.LISTENING) {
                                Res.string.dhikr_model_stop
                            } else {
                                Res.string.dhikr_model_start
                            },
                        ),
                        color = Color.White,
                    )
                }
            }

            RuntimeCard(
                status = state.status.name,
                phrase = state.phrase?.let { "${it.id} · ${it.text}" }.orEmpty(),
                detector = state.detectorState.name,
                score = state.score,
                count = state.count,
                error = state.error,
            )
        }
    }
}

@Composable
private fun RuntimeCard(
    status: String,
    phrase: String,
    detector: String,
    score: Float,
    count: Int,
    error: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = count.toString(),
                color = MohamedLoversPalette.GoldHighlight,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.dhikr_model_confirmed_count),
                color = Color.White.copy(alpha = 0.65f),
            )
            LinearProgressIndicator(
                progress = { score.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MohamedLoversPalette.GoldGlow,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Text(
                text = stringResource(Res.string.dhikr_model_score, score),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.dhikr_model_runtime_state, status, detector),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (phrase.isNotEmpty()) {
                Text(
                    text = phrase,
                    color = MohamedLoversPalette.GoldHighlight,
                    textAlign = TextAlign.Center,
                )
            }
            if (error != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33B23A48), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    color = Color(0xFFFFB3BA),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
