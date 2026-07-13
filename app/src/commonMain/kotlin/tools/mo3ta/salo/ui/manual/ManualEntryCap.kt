package tools.mo3ta.salo.ui.manual

/**
 * Clamp a raw digit string typed into a manual-entry field to the [remaining] daily
 * allowance. Empty input stays empty; anything above the cap (including values too large
 * to parse as Int) is pinned to [remaining]. Shared by every challenge's manual sheet so
 * external batches can't exceed the per-day cap.
 */
internal fun clampToRemaining(digits: String, remaining: Int): String = when {
    digits.isEmpty() -> ""
    else -> (digits.toIntOrNull()?.coerceAtMost(remaining) ?: remaining).toString()
}
