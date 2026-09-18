package tools.mo3ta.salo.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import tools.mo3ta.salo.analytics.AnalyticsManager
import tools.mo3ta.salo.analytics.AppAnalytics
import tools.mo3ta.salo.data.engagement.WEEKLY_GOAL_UNSET
import tools.mo3ta.salo.data.engagement.WeeklyGoalStore
import tools.mo3ta.salo.data.time.CAIRO_ZONE

/**
 * Self-contained weekly-goal card + sheet. Syncs [todayCount] into the local week ledger
 * and lets the user set / edit / clear the personal Saturday→Friday target.
 */
@Composable
fun WeeklyGoalSection(
    challengeId: String,
    todayCount: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDark: Boolean = true,
    docked: Boolean = false,
) {
    val store: WeeklyGoalStore = koinInject()
    val analytics: AnalyticsManager = koinInject()
    val today = remember { Clock.System.todayIn(CAIRO_ZONE) }
    var progress by remember(challengeId) {
        mutableStateOf(store.snapshot(challengeId, today))
    }
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(challengeId, todayCount) {
        store.syncFromTodayCount(challengeId, today, todayCount)
        progress = store.snapshot(challengeId, today)
    }

    WeeklyGoalBar(
        progress = progress,
        accent = accent,
        compact = compact,
        onDark = onDark,
        onClick = {
            analytics.logAction(
                AppAnalytics.OPEN_WEEKLY_GOAL,
                mapOf(
                    AppAnalytics.PARAM_CHALLENGE to challengeId,
                    AppAnalytics.PARAM_SOURCE to if (docked) "challenge" else "hub",
                ),
            )
            showSheet = true
        },
        modifier = modifier.then(
            if (docked) Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
            else Modifier,
        ),
    )

    SetWeeklyGoalSheet(
        isOpen = showSheet,
        currentGoal = progress.goal,
        accent = accent,
        onDismiss = { showSheet = false },
        onSave = { goal ->
            store.setGoal(challengeId, goal)
            analytics.logAction(
                AppAnalytics.SET_WEEKLY_GOAL,
                mapOf(
                    AppAnalytics.PARAM_CHALLENGE to challengeId,
                    AppAnalytics.PARAM_GOAL to goal.toString(),
                ),
            )
            progress = store.snapshot(challengeId, today)
            showSheet = false
        },
        onClear = {
            store.setGoal(challengeId, WEEKLY_GOAL_UNSET)
            progress = store.snapshot(challengeId, today)
            showSheet = false
        },
    )
}

@Composable
fun WeeklyGoalSection(
    challengeId: String,
    todayCountFlow: StateFlow<Int>,
    accent: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onDark: Boolean = true,
    docked: Boolean = false,
) {
    val todayCount by todayCountFlow.collectAsStateWithLifecycle()
    WeeklyGoalSection(
        challengeId = challengeId,
        todayCount = todayCount,
        accent = accent,
        modifier = modifier,
        compact = compact,
        onDark = onDark,
        docked = docked,
    )
}
