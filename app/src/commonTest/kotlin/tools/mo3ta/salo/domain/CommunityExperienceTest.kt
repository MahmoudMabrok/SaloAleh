package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommunityExperienceTest {

    @Test
    fun blankBody_isRejected_andWhitespaceIsTrimmed() {
        assertEquals(
            ExperienceDraftError.EmptyBody,
            validateExperienceDraft(draft(body = "  \n")),
        )
        val normalized = normalizeExperienceDraft(draft(name = "  ليلى  ", body = "  سلام  "))
        assertEquals("ليلى", normalized.displayName)
        assertEquals("سلام", normalized.body)
    }

    @Test
    fun emptyName_fallsBackOnNormalize() {
        val normalized = normalizeExperienceDraft(draft(name = "   ", body = "تجربة"))
        assertEquals(ExperienceLimits.ANONYMOUS_DISPLAY_NAME, normalized.displayName)
        assertEquals(6, ExperienceLimits.ANONYMOUS_DISPLAY_NAME.length)
        assertNull(validateExperienceDraft(draft(name = "   ", body = "تجربة")))
    }

    @Test
    fun lengthBounds() {
        assertNull(validateExperienceDraft(draft(name = "ا".repeat(40), body = "ب".repeat(1000))))
        assertEquals(
            ExperienceDraftError.NameTooLong,
            validateExperienceDraft(draft(name = "ا".repeat(41), body = "تمام")),
        )
        assertEquals(
            ExperienceDraftError.BodyTooLong,
            validateExperienceDraft(draft(body = "ب".repeat(1001))),
        )
    }

    @Test
    fun rawHtmlAndControls_areRejected_newlinesInBodyAreKept() {
        assertEquals(ExperienceDraftError.UnsafeContent, validateExperienceDraft(draft(body = "<b>سلام</b>")))
        assertEquals(ExperienceDraftError.UnsafeContent, validateExperienceDraft(draft(name = "a<b>", body = "سلام")))
        assertEquals(ExperienceDraftError.UnsafeContent, validateExperienceDraft(draft(body = "سلام\u0000")))
        assertEquals(ExperienceDraftError.UnsafeContent, validateExperienceDraft(draft(name = "سطر\nاسم", body = "سلام")))
        assertNull(validateExperienceDraft(draft(body = "سطر\nثان")))
    }

    @Test
    fun publishedRows_dropUnknownCategoryMarkupAndBlankIds() {
        assertNull(communityExperienceOrNull("id", "هدى", "نص", "other", "2026-06-15T10:00:00Z"))
        assertNull(communityExperienceOrNull("id", "هدى", "<i>نص</i>", "app", "2026-06-15T10:00:00Z"))
        assertNull(communityExperienceOrNull(" ", "هدى", "نص", "app", "2026-06-15T10:00:00Z"))
        val kept = communityExperienceOrNull(
            id = "abc",
            displayName = "  هدى  ",
            body = "  نص  ",
            category = "dhikr",
            createdAt = "2026-06-15T10:00:00Z",
        )
        assertEquals(
            CommunityExperience("abc", "هدى", "نص", ExperienceCategory.DHIKR, "2026-06-15T10:00:00Z"),
            kept,
        )
    }

    @Test
    fun datesRenderInCairo() {
        assertEquals("2026/06/15", formatExperienceDate("2026-06-15T10:00:00Z"))
        assertEquals("2026/01/16", formatExperienceDate("2026-01-15T22:30:00Z"))
        assertEquals("", formatExperienceDate("not-a-date"))
        assertTrue(formatExperienceDate("").isEmpty())
    }

    private fun draft(
        name: String = "هدى",
        body: String,
        category: ExperienceCategory = ExperienceCategory.APP,
    ) = ExperienceDraft(name, body, category)
}
