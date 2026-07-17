package tools.mo3ta.salo.ui.ghars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sqrt
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.ghars_garden_label
import tools.mo3ta.salo.generated.resources.ghars_gardens_count
import tools.mo3ta.salo.generated.resources.ghars_gardens_empty_body
import tools.mo3ta.salo.generated.resources.ghars_gardens_empty_title
import tools.mo3ta.salo.generated.resources.ghars_gardens_entry
import tools.mo3ta.salo.generated.resources.ghars_walk_hint

/**
 * "My Groves" — the user's completed gardens. A garden is one finished grove of [GROVE_SIZE] palms;
 * the number owned is the lifetime palm total divided by the grove size. The gallery is a plain text
 * list (rendering a grove per row was far too heavy), and tapping a row drops the user into a
 * full-screen walk through that exact grove.
 *
 * Both levels live here as an overlay driven by view-model flags (like the leaderboard/manual sheets),
 * so no app-level navigation is involved.
 */
@Composable
internal fun GharsGardensOverlay(
    lifetimePalms: Int,
    openGardenIndex: Int,
    onOpenGarden: (Int) -> Unit,
    onCloseGarden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gardenCount = gardensOwned(lifetimePalms)

    Box(Modifier.fillMaxSize().background(GharsColors.NightIndigo)) {
        if (openGardenIndex in 0 until gardenCount) {
            GardenWalkView(gardenIndex = openGardenIndex, onBack = onCloseGarden)
        } else {
            GardensGallery(
                gardenCount = gardenCount,
                onOpenGarden = onOpenGarden,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun GardensGallery(
    gardenCount: Int,
    onOpenGarden: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GharsColors.TextOnSky)
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(Res.string.ghars_gardens_entry),
                    color = GharsColors.TextOnSky,
                    fontSize = 20.sp,
                    fontFamily = arefRuqaaFamily(),
                )
                Text(
                    text = stringResource(Res.string.ghars_gardens_count, gardenCount),
                    color = GharsColors.TextOnSky.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = ibmPlexArabicFamily(),
                )
            }
        }

        if (gardenCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.ghars_gardens_empty_title),
                        color = GharsColors.Gold,
                        fontSize = 20.sp,
                        fontFamily = arefRuqaaFamily(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.ghars_gardens_empty_body),
                        color = GharsColors.TextOnSky.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontFamily = ibmPlexArabicFamily(),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                    )
                }
            }
        } else {
            // Newest grove first — the one the user just finished sits at the top.
            val gardens = remember(gardenCount) { (gardenCount - 1 downTo 0).toList() }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(gardens, key = { it }) { gardenIndex ->
                    GardenListRow(gardenIndex = gardenIndex, onClick = { onOpenGarden(gardenIndex) })
                }
            }
        }
    }
}

@Composable
private fun GardenListRow(gardenIndex: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GharsColors.PalmDeep.copy(alpha = 0.55f))
            .border(1.dp, GharsColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(GharsColors.Accent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Forest, null, tint = GharsColors.FrondLit, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = stringResource(Res.string.ghars_garden_label, gardenIndex + 1),
            color = GharsColors.TextOnSky,
            fontSize = 17.sp,
            fontFamily = arefRuqaaFamily(),
            modifier = Modifier.weight(1f),
        )
    }
}

// Vertical band of the canvas that maps to walkable ground, as fractions of height: touching near
// the top (horizon) sends the avatar to the furthest row, near the bottom brings it to the front.
private const val GROUND_TOP_FRACTION = 0.55f
private const val GROUND_BOTTOM_FRACTION = 0.82f

@Composable
private fun GardenWalkView(gardenIndex: Int, onBack: () -> Unit) {
    // The avatar chases a target position (across the width and in depth) the user sets by dragging or
    // tapping. Movement is constant speed, and the gait is driven off the live position so the figure
    // stands still the moment it arrives.
    val avatarX = remember(gardenIndex) { Animatable(0.5f) }
    val avatarDepth = remember(gardenIndex) { Animatable(1f) }
    var targetX by remember(gardenIndex) { mutableFloatStateOf(0.5f) }
    var targetDepth by remember(gardenIndex) { mutableFloatStateOf(1f) }
    var facingRight by remember(gardenIndex) { mutableStateOf(true) }

    LaunchedEffect(targetX, targetDepth) {
        val dx = targetX - avatarX.value
        val dd = targetDepth - avatarDepth.value
        val dist = sqrt(dx * dx + dd * dd)
        if (dist < 0.001f) return@LaunchedEffect
        val dur = (dist * 2600f).toInt().coerceAtLeast(120)
        launch { avatarX.animateTo(targetX, tween(dur, easing = LinearEasing)) }
        avatarDepth.animateTo(targetDepth, tween(dur, easing = LinearEasing))
    }

    fun aim(px: Float, py: Float, width: Int, heightPx: Int) {
        val fx = (px / width).coerceIn(0f, 1f)
        val fy = (py / heightPx)
        val depth = ((fy - GROUND_TOP_FRACTION) / (GROUND_BOTTOM_FRACTION - GROUND_TOP_FRACTION)).coerceIn(0f, 1f)
        facingRight = fx >= avatarX.value
        targetX = fx
        targetDepth = depth
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gardenIndex) {
                detectTapGestures { pos -> aim(pos.x, pos.y, size.width, size.height) }
            }
            .pointerInput(gardenIndex) {
                detectDragGestures { change, _ -> aim(change.position.x, change.position.y, size.width, size.height) }
            },
    ) {
        GardenWalkCanvas(
            gardenIndex = gardenIndex,
            avatarFraction = avatarX.value,
            avatarDepth = avatarDepth.value,
            gaitDistance = avatarX.value * 600f + avatarDepth.value * 300f,
            moving = avatarX.isRunning || avatarDepth.isRunning,
            facingRight = facingRight,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GharsColors.TextOnSky)
            }
            Text(
                text = stringResource(Res.string.ghars_garden_label, gardenIndex + 1),
                color = GharsColors.TextOnSky,
                fontSize = 18.sp,
                fontFamily = arefRuqaaFamily(),
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Text(
            text = stringResource(Res.string.ghars_walk_hint),
            color = GharsColors.TextOnSky.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = ibmPlexArabicFamily(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        )
    }
}
