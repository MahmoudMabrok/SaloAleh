package tools.mo3ta.salo.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import tools.mo3ta.salo.audio.TakbeerSoundPlayer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TakbeerSessionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTakbeerSoundPlayer : TakbeerSoundPlayer {
        var playCount = 0
        var stopCount = 0
        override suspend fun play() {
            playCount++
        }
        override fun stop() {
            stopCount++
        }
    }

    @Test
    fun input_keeps_only_digits_and_caps_length() {
        val vm = TakbeerSessionViewModel(FakeTakbeerSoundPlayer())
        vm.onPeopleInputChange("a7b")
        assertEquals("7", vm.state.value.peopleCountInput)
        vm.onPeopleInputChange("123456")
        assertEquals("12", vm.state.value.peopleCountInput)
    }

    @Test
    fun valid_count_respects_allowed_range() {
        val vm = TakbeerSessionViewModel(FakeTakbeerSoundPlayer())
        vm.onPeopleInputChange("6")
        assertEquals(6, vm.state.value.validCount)
        assertTrue(vm.state.value.canStart)

        vm.onPeopleInputChange("1")
        assertNull(vm.state.value.validCount)
        assertFalse(vm.state.value.canStart)

        vm.onPeopleInputChange("99")
        assertNull(vm.state.value.validCount)

        vm.onPeopleInputChange("")
        assertNull(vm.state.value.validCount)
    }

    @Test
    fun step_clamps_to_allowed_range() {
        val vm = TakbeerSessionViewModel(FakeTakbeerSoundPlayer())
        vm.onPeopleInputChange("10")
        vm.onStep(1)
        assertEquals("10", vm.state.value.peopleCountInput)

        vm.onPeopleInputChange("2")
        vm.onStep(-1)
        assertEquals("2", vm.state.value.peopleCountInput)

        vm.onPeopleInputChange("5")
        vm.onStep(1)
        assertEquals("6", vm.state.value.peopleCountInput)
    }

    @Test
    fun start_session_is_rejected_for_invalid_input() {
        val vm = TakbeerSessionViewModel(FakeTakbeerSoundPlayer())
        vm.onPeopleInputChange("1")
        vm.startSession()
        assertEquals(TakbeerPhase.SETUP, vm.state.value.phase)
    }

    @Test
    fun session_cycles_to_user_turn_then_advances_rounds() {
        val sound = FakeTakbeerSoundPlayer()
        val vm = TakbeerSessionViewModel(sound)
        vm.onPeopleInputChange("5")

        vm.startSession()
        testDispatcher.scheduler.advanceUntilIdle()

        // Audio cycles through the four numbered participants and parks on the user.
        assertEquals(TakbeerPhase.RUNNING, vm.state.value.phase)
        assertEquals(4, vm.state.value.currentTurn)
        assertTrue(vm.state.value.isUserTurn)
        assertEquals(0, vm.state.value.roundsCompleted)
        assertEquals(4, sound.playCount)

        vm.onUserDone()
        testDispatcher.scheduler.advanceUntilIdle()

        // A full round is completed and the next round again parks on the user.
        assertEquals(1, vm.state.value.roundsCompleted)
        assertTrue(vm.state.value.isUserTurn)
        assertEquals(8, sound.playCount)

        vm.stopSession()
        assertEquals(TakbeerPhase.SETUP, vm.state.value.phase)
        assertEquals(-1, vm.state.value.currentTurn)
    }
}
