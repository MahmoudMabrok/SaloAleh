package tools.mo3ta.salo.input

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Tells a tap a person made apart from a tap a program injected.
 *
 * Auto-clicker apps drive the salawat counter through
 * [android.accessibilityservice.AccessibilityService.dispatchGesture], which fabricates a
 * [MotionEvent] instead of reading a digitizer. The fabricated event never belongs to a real
 * input device, and that is what gives it away.
 *
 * Measured on an Android 16 device, real finger taps against a running auto-clicker:
 *
 * ```
 * injected  deviceId=-1  isVirtual=true   toolType=UNKNOWN  flags=0x800  source=0x1002
 * real      deviceId=3   isVirtual=false  toolType=FINGER   flags=0x0    source=0x5002
 * ```
 *
 * Of the four fields that separate them, this keys on device identity — "the event did not come
 * from a physical input device" — because that is the property injection cannot avoid.
 * `toolType` is deliberately *not* a trigger: it varies across OEM digitizers, and a device
 * reporting `UNKNOWN` for real fingers would find the app unusable.
 *
 * Known limitation: this only sees events injected above the input stack. A rooted clicker
 * writing to `/dev/input` enters at the kernel evdev layer and is indistinguishable from real
 * hardware here.
 */
object AutoClickHeuristic {

    /**
     * `MotionEvent.FLAG_IS_ACCESSIBILITY_EVENT`. Hidden from the public SDK (`@hide @TestApi`),
     * but the bit rides in the int returned by the public [MotionEvent.getFlags], so reading it
     * needs no reflection and touches no blocklisted API. Treated as corroboration only — the
     * device-identity checks below stand on public API and carry the rule on their own.
     */
    const val FLAG_IS_ACCESSIBILITY_EVENT = 0x800

    /**
     * @param isTouchscreen whether the event claims a touchscreen source. Non-touch sources
     *   (mouse, stylus, gamepad) are left alone so external-input users are unaffected.
     * @param deviceId the originating input device id; injected events carry a negative id.
     * @param isVirtualDevice whether that id resolves to a virtual rather than physical device.
     * @param flags raw [MotionEvent.getFlags].
     */
    fun isSynthetic(
        isTouchscreen: Boolean,
        deviceId: Int,
        isVirtualDevice: Boolean,
        flags: Int,
    ): Boolean {
        if (!isTouchscreen) return false
        return deviceId < 0 ||
            isVirtualDevice ||
            flags and FLAG_IS_ACCESSIBILITY_EVENT != 0
    }
}

/**
 * Applies [AutoClickHeuristic] to a live event.
 *
 * Note [InputDevice.getDevice] returns a device named `Virtual` for injected events rather than
 * null, so presence of a device proves nothing — [InputDevice.isVirtual] is the question.
 */
fun MotionEvent.isSyntheticTap(): Boolean {
    val device = runCatching { InputDevice.getDevice(deviceId) }.getOrNull()
    return AutoClickHeuristic.isSynthetic(
        isTouchscreen = source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN,
        deviceId = deviceId,
        isVirtualDevice = device?.isVirtual == true,
        flags = flags,
    )
}
