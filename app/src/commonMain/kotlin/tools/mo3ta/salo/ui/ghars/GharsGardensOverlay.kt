package tools.mo3ta.salo.ui.ghars

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.ghars_garden_label
import tools.mo3ta.salo.generated.resources.ghars_gardens_count
import tools.mo3ta.salo.generated.resources.ghars_gardens_empty_body
import tools.mo3ta.salo.generated.resources.ghars_gardens_empty_title
import tools.mo3ta.salo.generated.resources.ghars_gardens_entry
import tools.mo3ta.salo.generated.resources.ghars_walk_hint

/**
 * "My Groves" — the user's completed gardens. A garden is one finished grove of [GROVE_SIZE] palms;
 * the number owned is the lifetime palm total divided by the grove size. The gallery lists them, and
 * tapping one drops the user into a full-screen walk through that exact grove.
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(gardens, key = { it }) { gardenIndex ->
                    GardenCard(gardenIndex = gardenIndex, onClick = { onOpenGarden(gardenIndex) })
                }
            }
        }
    }
}

@Composable
private fun GardenCard(gardenIndex: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        GardenThumbnail(gardenIndex = gardenIndex, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xFF0B0A1E).copy(alpha = 0.72f)),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.ghars_garden_label, gardenIndex + 1),
                color = GharsColors.TextOnSky,
                fontSize = 14.sp,
                fontFamily = arefRuqaaFamily(),
            )
        }
    }
}

@Composable
private fun GardenWalkView(gardenIndex: Int, onBack: () -> Unit) {
    // The avatar chases a target fraction the user sets by dragging/tapping. Movement is constant
    // speed (duration scales with distance), and the gait is driven off the live position so the
    // figure stands still the moment it arrives.
    val avatarX = remember(gardenIndex) { Animatable(0.5f) }
    var target by remember(gardenIndex) { mutableFloatStateOf(0.5f) }
    var facingRight by remember(gardenIndex) { mutableStateOf(true) }

    LaunchedEffect(target) {
        val dist = abs(target - avatarX.value)
        if (dist < 0.001f) return@LaunchedEffect
        avatarX.animateTo(target, tween((dist * 2600f).toInt().coerceAtLeast(120), easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gardenIndex) {
                detectTapGestures { pos ->
                    val f = (pos.x / size.width).coerceIn(0f, 1f)
                    facingRight = f >= avatarX.value
                    target = f
                }
            }
            .pointerInput(gardenIndex) {
                detectDragGestures { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    facingRight = f >= avatarX.value
                    target = f
                }
            },
    ) {
        GardenWalkCanvas(
            gardenIndex = gardenIndex,
            avatarFraction = avatarX.value,
            gaitDistance = avatarX.value * 600f,
            moving = avatarX.isRunning,
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
