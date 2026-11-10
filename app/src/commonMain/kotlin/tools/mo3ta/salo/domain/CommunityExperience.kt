package tools.mo3ta.salo.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A short experience someone shared about the app or about dhikr.
 *
 * Limits stay aligned with community-api/validate.ts. The server is the authority:
 * it assigns [id] and [createdAt], and it keeps new submissions out of the published list.
 */
enum class ExperienceCategory(val wire: String) {
    APP("app"),
    DHIKR("dhikr"),
    ;

    companion object {
        fun fromWire(value: String): ExperienceCategory? = entries.firstOrNull { it.wire == value }
    }
}

data class CommunityExperience(
    val id: String,
    val displayName: String,
    val body: String,
    val category: ExperienceCategory,
    val createdAt: String,
)

data class ExperienceDraft(
    val displayName: String,
    val body: String,
    val category: ExperienceCategory,
)

sealed class ExperienceDraftError {
    data object EmptyBody : ExperienceDraftError()
    data object BodyTooLong : ExperienceDraftError()
    data object NameTooLong : ExperienceDraftError()
    data object UnsafeContent : ExperienceDraftError()
}

object ExperienceLimits {
    const val NAME_MAX = 40
    const val BODY_MAX = 1000
    const val ANONYMOUS_DISPLAY_NAME = "أخ/أخت"
}

enum class ExperiencesFailure {
    NotConfigured,
    Unavailable,
    RateLimited,
    Rejected,
}

class ExperiencesException(val failure: ExperiencesFailure) : Exception()

private const val ID_MAX = 80
private const val CREATED_AT_MAX = 40
private val CAIRO = TimeZone.of("Africa/Cairo")

fun validateExperienceDraft(draft: ExperienceDraft): ExperienceDraftError? {
    val name = draft.displayName.trim()
    val body = draft.body.trim()
    if (name.hasUnsafeContent(inName = true) || body.hasUnsafeContent(inName = false)) {
        return ExperienceDraftError.UnsafeContent
    }
    if (name.length > ExperienceLimits.NAME_MAX) return ExperienceDraftError.NameTooLong
    if (body.isEmpty()) return ExperienceDraftError.EmptyBody
    if (body.length > ExperienceLimits.BODY_MAX) return ExperienceDraftError.BodyTooLong
    return null
}

fun normalizeExperienceDraft(draft: ExperienceDraft): ExperienceDraft {
    val name = draft.displayName.trim().ifEmpty { ExperienceLimits.ANONYMOUS_DISPLAY_NAME }
    return draft.copy(displayName = name, body = draft.body.trim())
}

/**
 * Accept a published row only when it already satisfies the submission rules.
 * Client-supplied ids are not created here; a blank or oversized id is dropped.
 */
fun communityExperienceOrNull(
    id: String,
    displayName: String,
    body: String,
    category: String,
    createdAt: String,
): CommunityExperience? {
    val parsedCategory = ExperienceCategory.fromWire(category) ?: return null
    if (id.isBlank() || id.length > ID_MAX || id.hasMarkup()) return null
    if (createdAt.isBlank() || createdAt.length > CREATED_AT_MAX || createdAt.hasMarkup()) return null
    if (displayName.isBlank()) return null
    val draft = ExperienceDraft(displayName, body, parsedCategory)
    if (validateExperienceDraft(draft) != null) return null
    val normalized = normalizeExperienceDraft(draft)
    return CommunityExperience(
        id = id,
        displayName = normalized.displayName,
        body = normalized.body,
        category = parsedCategory,
        createdAt = createdAt,
    )
}

/** Cairo calendar date, `yyyy/MM/dd`. An unreadable timestamp renders as nothing. */
fun formatExperienceDate(createdAtIso: String): String {
    val instant = runCatching { Instant.parse(createdAtIso) }.getOrNull() ?: return ""
    val local = instant.toLocalDateTime(CAIRO)
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    return "${local.year}/$month/$day"
}

private fun String.hasMarkup(): Boolean = any { it == '<' || it == '>' }

private fun String.hasUnsafeContent(inName: Boolean): Boolean {
    if (hasMarkup()) return true
    return any { char ->
        if (!char.isISOControl()) return@any false
        if (!inName && (char == '\n' || char == '\r' || char == '\t')) return@any false
        true
    }
}
