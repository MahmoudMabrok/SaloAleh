package tools.mo3ta.salo.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.data.hadith.HadithItem
import tools.mo3ta.salo.data.media.MediaItem
import tools.mo3ta.salo.data.media.MediaType
import tools.mo3ta.salo.presentation.HadithListViewModel

private val HLGold      = Color(0xFFD4AF37)
private val HLDeepNavy  = Color(0xFF09142B)
private val HLMidNavy   = Color(0xFF0F2040)
private val HLCardTop   = Color(0xFF132545)
private val HLCream     = Color(0xFFFFF8E7)
private val HLMutedGold = Color(0xFFA89020)
private val HLActionBg  = Color(0xFF0A1A30)

private val TypeVideo    = Color(0xFF2E6B3E)
private val TypePlaylist = Color(0xFF1E4A7A)
private val TypeChannel  = Color(0xFF6B2E1E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithListScreen(
    onBack: () -> Unit,
    viewModel: HadithListViewModel = koinViewModel(),
) {
    val analyticsManager: AnalyticsManager = koinInject()

    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit){
        analyticsManager.logView("HadithListScreen")
    }

    Scaffold(
        containerColor = HLDeepNavy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "فضل الصلاة على النبي ﷺ",
                            color = HLGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = if (selectedTab == 0)
                                "${state.texts.size} حديث وأثر"
                            else
                                "${state.media.size} مقطع وقائمة",
                            color = HLMutedGold,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = HLGold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A1528)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Tab bar ──────────────────────────────────────────────
            SegmentedTabBar(
                tabs = listOf("النصوص", "الوسائط"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            GoldDivider(widthFraction = 1f, alpha = 0.15f)

            Crossfade(targetState = selectedTab, animationSpec = tween(260), label = "tab") { tab ->
                when (tab) {
                    0 -> TextTab(
                        items = state.texts,
                        isLoading = state.isLoadingTexts,
                        loaded = state.textsLoaded,
                    )
                    else -> MediaTab(
                        items = state.media,
                        isLoading = state.isLoadingMedia,
                        loaded = state.mediaLoaded,
                    )
                }
            }
        }
    }
}

// ── Text tab ─────────────────────────────────────────────────────────────────

@Composable
private fun TextTab(items: List<HadithItem>, isLoading: Boolean, loaded: Boolean) {
    when {
        isLoading -> LoadingState()
        loaded && items.isEmpty() -> EmptyState(message = "لا يوجد محتوى")
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            itemsIndexed(items) { index, item ->
                HadithListItem(item = item, index = index)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Media tab ─────────────────────────────────────────────────────────────────

@Composable
private fun MediaTab(items: List<MediaItem>, isLoading: Boolean, loaded: Boolean) {
    val uriHandler = LocalUriHandler.current
    when {
        isLoading -> LoadingState()
        loaded && items.isEmpty() -> EmptyState(message = "لا يوجد محتوى")
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            itemsIndexed(items) { _, item ->
                MediaCard(item = item, onOpen = { uriHandler.openUri(item.url) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = HLGold)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = HLMutedGold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Media card ────────────────────────────────────────────────────────────────

@Composable
private fun MediaCard(item: MediaItem, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)

    val (typeLabel, typeBg) = when (item.type) {
        MediaType.VIDEO    -> "فيديو" to TypeVideo
        MediaType.PLAYLIST -> "قائمة" to TypePlaylist
        MediaType.CHANNEL  -> "قناة"  to TypeChannel
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HLCardTop, HLMidNavy)))
            .border(1.dp, HLGold.copy(alpha = 0.3f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
    ) {
        // Top shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, HLGold.copy(alpha = 0.4f), Color.Transparent),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Play / type icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HLGold.copy(alpha = 0.1f))
                    .border(1.dp, HLGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = HLGold.copy(alpha = 0.75f),
                    modifier = Modifier.size(24.dp),
                )
            }

            // Text block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = HLCream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    style = TextStyle(
                        textDirection = if (item.language.contains("Arabic")) TextDirection.Rtl else TextDirection.Ltr,
                    ),
                    textAlign = if (item.language.contains("Arabic")) TextAlign.Start else TextAlign.Start,
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Type badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeBg.copy(alpha = 0.8f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(typeLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // Language badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(HLGold.copy(alpha = 0.12f))
                            .border(1.dp, HLGold.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(item.language, color = HLMutedGold, fontSize = 10.sp)
                    }
                }
            }

            // External link icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "فتح",
                tint = HLGold.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Hadith card (text tab) ────────────────────────────────────────────────────

@Composable
private fun HadithListItem(item: HadithItem, index: Int) {
    val shape = RoundedCornerShape(20.dp)
    val shareContent = "${item.title}\n\n${item.text}\n\n${item.source}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(HLCardTop, HLMidNavy, HLDeepNavy)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(HLGold.copy(alpha = 0.55f), HLGold.copy(alpha = 0.08f)),
                ),
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, HLGold.copy(0.35f), HLGold.copy(0.7f), HLGold.copy(0.35f), Color.Transparent),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(HLGold.copy(alpha = 0.15f))
                        .border(1.dp, HLGold.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = HLGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("✦  ❖  ✦", color = HLGold.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = item.title,
                color = HLGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            GoldDivider(widthFraction = 0.85f)
            Spacer(Modifier.height(12.dp))

            Text(
                text = item.text,
                color = HLCream,
                fontSize = 13.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.source,
                color = HLMutedGold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            GoldDivider(widthFraction = 0.5f, alpha = 0.2f)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CopyActionButton(text = shareContent, modifier = Modifier.weight(1f))
                ActionButton(
                    icon = { Icon(Icons.Default.Share, contentDescription = null, tint = HLGold, modifier = Modifier.size(15.dp)) },
                    label = "مشاركة",
                    onClick = { shareText(shareContent) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Shared tab bar ────────────────────────────────────────────────────────────

@Composable
private fun SegmentedTabBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF0A1528))
            .border(1.dp, HLGold.copy(alpha = 0.2f), shape)
            .padding(4.dp),
    ) {
        Row {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) HLGold.copy(alpha = 0.2f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tab_bg_$index",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) HLGold else HLMutedGold,
                    animationSpec = tween(200),
                    label = "tab_text_$index",
                )
                val borderAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.5f else 0f,
                    animationSpec = tween(200),
                    label = "tab_border_$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bgColor)
                        .border(1.dp, HLGold.copy(alpha = borderAlpha), RoundedCornerShape(9.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun CopyActionButton(text: String, modifier: Modifier = Modifier) {
    var copied by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (copied) HLGold.copy(alpha = 0.18f) else HLActionBg,
        animationSpec = tween(200), label = "copy_bg",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (copied) 0.75f else 0.25f,
        animationSpec = tween(200), label = "copy_border",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, HLGold.copy(alpha = borderAlpha), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { if (!copied) { copyToClipboard(text); copied = true } }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Crossfade(targetState = copied, animationSpec = tween(200), label = "copy_icon") { isCopied ->
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = HLGold,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = if (copied) "تم النسخ" else "نسخ",
                color = HLGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(HLActionBg)
            .border(1.dp, HLGold.copy(alpha = 0.25f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
            Text(text = label, color = HLGold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GoldDivider(widthFraction: Float = 1f, alpha: Float = 0.4f) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, HLGold.copy(alpha = alpha), Color.Transparent),
                ),
            ),
    )
}
