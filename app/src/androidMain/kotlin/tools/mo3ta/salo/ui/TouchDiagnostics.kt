package tools.mo3ta.salo.ui

import android.view.InputDevice
import android.view.MotionEvent
import co.touchlab.kermit.Logger
import tools.mo3ta.salo.BuildConfig

/**
 * Temporary diagnostic for the auto-click guard.
 *
 * Auto-clicker apps inject taps through [android.accessibilityservice.AccessibilityService.dispatchGesture],
 * which fabricates a [MotionEvent] rather than reading a real digitizer. AOSP's `MotionEventInjector`
 * passes `KeyCharacterMap.VIRTUAL_KEYBOARD` (0) as the device id and marks the event at the policy
 * layer, but it is not documented whether any of that survives to the app — so measure it rather
 * than guess.
 *
 * Install an auto-clicker, point it at the salawat button, and compare against a real finger:
 *
 * ```
 * adb logcat -s SaloTouch
 * ```
 *
 * The field that differs between the two is the one the guard should key on. Both counting
 * surfaces are wired up — the main screen via `MainActivity.dispatchTouchEvent` and the floating
 * bubble via its own touch listener, since the bubble is a separate overlay window whose taps
 * never reach the activity.
 *
 * This can only see events injected above the input stack, which covers accessibility-based
 * clickers (the Play Store norm). A rooted clicker writing to `/dev/input` enters at the kernel
 * evdev layer and is indistinguishable from real hardware here.
 *
 * Delete this file once the signal is confirmed.
 */
object TouchDiagnostics {

    private val log = Logger.withTag("SaloTouch")

    /** Known [MotionEvent] flag bits, including ones hidden from the public SDK. */
    private val FLAG_NAMES = listOf(
        0x1 to "WINDOW_IS_OBSCURED",
        0x2 to "WINDOW_IS_PARTIALLY_OBSCURED",
        0x8 to "IS_GENERATED_GESTURE(hidden)",
        0x20 to "CANCELED",
        0x800 to "IS_ACCESSIBILITY_EVENT(hidden)",
    )

    /**
     * Logs one line per touch-down. Only `ACTION_DOWN` is reported — `ACTION_MOVE` fires
     * continuously and carries the same identity fields, so it would only flood the log.
     */
    fun log(event: MotionEvent) {
        if (!BuildConfig.DEBUG) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return

        val device = runCatching { InputDevice.getDevice(event.deviceId) }.getOrNull()

        log.i {
            "deviceId=${event.deviceId} " +
                "resolvesToDevice=${device != null} " +
                "deviceName=${device?.name ?: "—"} " +
                "isVirtual=${device?.isVirtual ?: "—"} " +
                "source=0x${event.source.toString(16)}(${describeSource(event.source)}) " +
                "toolType=${describeToolType(event.getToolType(0))} " +
                "flags=0x${event.flags.toString(16)}[${describeFlags(event.flags)}] " +
                "pressure=${event.pressure} size=${event.size} " +
                "x=${event.rawX} y=${event.rawY}"
        }
    }

    private fun describeSource(source: Int): String = buildList {
        if (source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN) add("TOUCHSCREEN")
        if (source and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD) add("TOUCHPAD")
        if (source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) add("MOUSE")
        if (source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) add("STYLUS")
        if (isEmpty()) add("UNKNOWN")
    }.joinToString("|")

    private fun describeToolType(toolType: Int): String = when (toolType) {
        MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
        MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
        MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
        MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
        MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
        else -> "OTHER($toolType)"
    }

    private fun describeFlags(flags: Int): String =
        FLAG_NAMES.filter { (bit, _) -> flags and bit != 0 }
            .joinToString("|") { (_, name) -> name }
            .ifEmpty { "none" }
}
