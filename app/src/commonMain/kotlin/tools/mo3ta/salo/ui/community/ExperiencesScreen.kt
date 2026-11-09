package tools.mo3ta.salo.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.domain.CommunityExperience
import tools.mo3ta.salo.domain.ExperienceCategory
import tools.mo3ta.salo.domain.ExperienceDraftError
import tools.mo3ta.salo.domain.ExperienceLimits
import tools.mo3ta.salo.domain.ExperiencesFailure
import tools.mo3ta.salo.domain.formatExperienceDate
import tools.mo3ta.salo.generated.resources.Res
import tools.mo3ta.salo.generated.resources.*
import tools.mo3ta.salo.presentation.ExperiencesUiState
import tools.mo3ta.salo.presentation.ExperiencesViewModel
import tools.mo3ta.salo.ui.components.MohamedLoversPalette
import tools.mo3ta.salo.ui.shareText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExperiencesScreen(onBack: () -> Unit) {
    val viewModel: ExperiencesViewModel = koinViewModel()
    val analyticsManager: AnalyticsManager = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        analyticsManager.logView("ExperiencesScreen")
        viewModel.refresh()
    }
    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) analyticsManager.logAction(AppAnalytics.EXPERIENCE_SUBMITTED)
    }

    Scaffold(
        containerColor = MohamedLoversPalette.DeepBlue,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.experiences_title),
                        color = MohamedLoversPalette.GoldGlow,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.mohamed_lovers_back_cd),
                            tint = MohamedLoversPalette.GoldGlow,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16213e)),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.experiences_intro),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )

            SectionHeader(stringResource(Res.string.experiences_list_header))
            PublishedSection(
                state = state,
                onRetry = viewModel::refresh,
                onShare = { item, text ->
                    shareText(text)
                    analyticsManager.logAction(AppAnalytics.EXPERIENCE_SHARED)
                },
            )

            val showForm = state.loadError != ExperiencesFailure.NotConfigured &&
                !(state.loading && state.experiences.isEmpty())
            if (showForm) {
                SectionHeader(stringResource(Res.string.experiences_form_header))
                SubmitForm(state = state, viewModel = viewModel)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PublishedSection(
    state: ExperiencesUiState,
    onRetry: () -> Unit,
    onShare: (CommunityExperience, String) -> Unit,
) {
    when {
        state.loading && state.experiences.isEmpty() -> {
            CircularProgressIndicator(
                color = MohamedLoversPalette.GoldGlow,
                modifier = Modifier.padding(vertical = 12.dp).size(28.dp),
            )
        }
        state.loadError != null && state.experiences.isEmpty() -> {
            Text(
                text = stringResource(loadErrorText(state.loadError)),
                color = Color(0xFFFF8A80),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(Res.string.experiences_retry),
                    color = MohamedLoversPalette.GoldGlow,
                )
            }
        }
        state.experiences.isEmpty() -> {
            Text(
                text = stringResource(Res.string.experiences_empty),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
        else -> {
            if (state.loadError != null) {
                Text(
                    text = stringResource(loadErrorText(state.loadError)),
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(Res.string.experiences_retry),
                        color = MohamedLoversPalette.GoldGlow,
                    )
                }
            }
            state.experiences.forEach { item ->
                ExperienceCard(item = item, onShare = onShare)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ExperienceCard(
    item: CommunityExperience,
    onShare: (CommunityExperience, String) -> Unit,
) {
    val categoryLabel = categoryLabel(item.category)
    val date = formatExperienceDate(item.createdAt)
    val meta = if (date.isEmpty()) {
        categoryLabel
    } else {
        stringResource(Res.string.experiences_meta, categoryLabel, date)
    }
    val shareBody = stringResource(Res.string.experiences_share_text, item.displayName, categoryLabel, item.body)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, MohamedLoversPalette.GoldGlow.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = item.displayName,
            color = MohamedLoversPalette.GoldGlow,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = meta,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.body,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        TextButton(
            onClick = { onShare(item, shareBody) },
            colors = ButtonDefaults.textButtonColors(contentColor = MohamedLoversPalette.GoldGlow),
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.experiences_share))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubmitForm(
    state: ExperiencesUiState,
    viewModel: ExperiencesViewModel,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = MohamedLoversPalette.GoldGlow,
        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
        focusedLabelColor = MohamedLoversPalette.GoldGlow,
        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
        cursorColor = MohamedLoversPalette.GoldGlow,
        disabledTextColor = Color.White.copy(alpha = 0.6f),
        disabledBorderColor = Color.White.copy(alpha = 0.15f),
        disabledLabelColor = Color.White.copy(alpha = 0.35f),
    )

    OutlinedTextField(
        value = state.displayName,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(Res.string.experiences_name_label)) },
        placeholder = { Text(stringResource(Res.string.experiences_name_hint)) },
        singleLine = true,
        enabled = !state.submitting,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CategoryChip(
            label = stringResource(Res.string.experiences_category_app),
            selected = state.category == ExperienceCategory.APP,
            enabled = !state.submitting,
            onClick = { viewModel.onCategoryChange(ExperienceCategory.APP) },
        )
        CategoryChip(
            label = stringResource(Res.string.experiences_category_dhikr),
            selected = state.category == ExperienceCategory.DHIKR,
            enabled = !state.submitting,
            onClick = { viewModel.onCategoryChange(ExperienceCategory.DHIKR) },
        )
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.body,
        onValueChange = viewModel::onBodyChange,
        label = { Text(stringResource(Res.string.experiences_body_label)) },
        enabled = !state.submitting,
        minLines = 4,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(
            Res.string.experiences_body_count,
            state.body.length,
            ExperienceLimits.BODY_MAX,
        ),
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )

    state.draftError?.let { error ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(draftErrorText(error)),
            color = Color(0xFFFF8A80),
            fontSize = 13.sp,
        )
    }

    if (state.submitSuccess) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.experiences_sent),
            color = MohamedLoversPalette.GoldGlow,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        TextButton(onClick = viewModel::dismissSubmitNotice) {
            Text(
                text = stringResource(Res.string.experiences_sent_dismiss),
                color = MohamedLoversPalette.GoldGlow,
            )
        }
    }

    state.submitFailure?.let { failure ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(submitFailureText(failure)),
            color = Color(0xFFFF8A80),
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = viewModel::submit,
        enabled = !state.submitting && state.body.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MohamedLoversPalette.GoldGlow,
            contentColor = Color(0xFF0f0f1a),
            disabledContainerColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.35f),
            disabledContentColor = Color(0xFF0f0f1a).copy(alpha = 0.7f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.submitting) {
            CircularProgressIndicator(
                color = Color(0xFF0f0f1a),
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = stringResource(Res.string.experiences_submit),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.2f),
            selectedLabelColor = MohamedLoversPalette.GoldGlow,
            labelColor = Color.White.copy(alpha = 0.7f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.White.copy(alpha = 0.2f),
            selectedBorderColor = MohamedLoversPalette.GoldGlow.copy(alpha = 0.5f),
            enabled = enabled,
            selected = selected,
        ),
    )
}

@Composable
private fun categoryLabel(category: ExperienceCategory): String = when (category) {
    ExperienceCategory.APP -> stringResource(Res.string.experiences_category_app)
    ExperienceCategory.DHIKR -> stringResource(Res.string.experiences_category_dhikr)
}

private fun loadErrorText(failure: ExperiencesFailure) = when (failure) {
    ExperiencesFailure.NotConfigured -> Res.string.experiences_not_configured
    ExperiencesFailure.Unavailable,
    ExperiencesFailure.RateLimited,
    ExperiencesFailure.Rejected,
    -> Res.string.experiences_error
}

private fun submitFailureText(failure: ExperiencesFailure) = when (failure) {
    ExperiencesFailure.NotConfigured -> Res.string.experiences_not_configured
    ExperiencesFailure.RateLimited -> Res.string.experiences_rate_limited
    ExperiencesFailure.Rejected -> Res.string.experiences_rejected
    ExperiencesFailure.Unavailable -> Res.string.experiences_submit_error
}

private fun draftErrorText(error: ExperienceDraftError) = when (error) {
    ExperienceDraftError.EmptyBody -> Res.string.experiences_validation_empty
    ExperienceDraftError.UnsafeContent -> Res.string.experiences_validation_unsafe
    ExperienceDraftError.NameTooLong,
    ExperienceDraftError.BodyTooLong,
    -> Res.string.experiences_validation_too_long
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = MohamedLoversPalette.GoldGlow.copy(alpha = 0.7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}
