package tools.mo3ta.salo.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tools.mo3ta.salo.TestApplication
import tools.mo3ta.salo.domain.Achievement
import tools.mo3ta.salo.domain.BadgeType
import tools.mo3ta.salo.presentation.MohamedLoversUiState
import tools.mo3ta.salo.ui.components.MohamedLoversInfoSheet

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
@OptIn(ExperimentalTestApi::class)
class ScreenSmokeTest {

    @Test
    fun onboardingScreen_renders() = runComposeUiTest {
        setContent {
            OnboardingScreen(onDone = {})
        }
        waitForIdle()
    }

    @Test
    fun reviewDialog_renders() = runComposeUiTest {
        setContent {
            ReviewDialog(
                onGoToStore = {},
                onDismiss = {},
            )
        }
        waitForIdle()
    }

    @Test
    fun nicknamePromptDialog_renders() = runComposeUiTest {
        setContent {
            NicknamePromptDialog(
                onSave = {},
                onDismiss = {},
            )
        }
        waitForIdle()
    }

    @Test
    fun achievementCelebrationDialog_rendersStreakBadge() = runComposeUiTest {
        setContent {
            AchievementCelebrationDialog(
                achievement = Achievement.StreakBadge(
                    type = BadgeType.STREAK_7,
                    earnedDate = LocalDate(2026, 5, 25),
                ),
                onDismiss = {},
            )
        }
        waitForIdle()
    }

    @Test
    fun notificationRationaleDialog_renders() = runComposeUiTest {
        setContent {
            NotificationRationaleDialog(
                onAllow = {},
                onDismiss = {},
            )
        }
        waitForIdle()
    }

    @Test
    fun infoSheet_rendersWhenOpen() = runComposeUiTest {
        setContent {
            MohamedLoversInfoSheet(
                isOpen = true,
                state = MohamedLoversUiState(isLoading = false),
                onDismiss = {},
                onCopyWinnerCode = {},
                onToggleLeaderboardType = {},
            )
        }
        waitForIdle()
    }

    @Test
    fun fcmPermissionReminderDialog_renders() = runComposeUiTest {
        setContent {
            FcmPermissionReminderDialog(
                onAllow = {},
                onDismiss = {},
            )
        }
        waitForIdle()
    }
}
