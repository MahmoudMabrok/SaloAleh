package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateConfigTest {

    @Test
    fun higher_patch_is_newer() {
        assertTrue(isNewerVersion("3.9.2", "3.9.1"))
    }

    @Test
    fun higher_minor_is_newer() {
        assertTrue(isNewerVersion("3.10.0", "3.9.9"))
    }

    @Test
    fun double_digit_minor_beats_string_ordering() {
        // "3.10" would sort before "3.9" as text; numeric comparison must win.
        assertTrue(isNewerVersion("3.10.0", "3.9.1"))
    }

    @Test
    fun higher_major_is_newer() {
        assertTrue(isNewerVersion("4.0.0", "3.9.1"))
    }

    @Test
    fun equal_version_is_not_newer() {
        assertFalse(isNewerVersion("3.9.1", "3.9.1"))
    }

    @Test
    fun lower_version_is_not_newer() {
        assertFalse(isNewerVersion("3.9.0", "3.9.1"))
        assertFalse(isNewerVersion("2.9.9", "3.0.0"))
    }

    @Test
    fun missing_trailing_component_counts_as_zero() {
        assertFalse(isNewerVersion("3.9", "3.9.0"))
        assertTrue(isNewerVersion("3.9.1", "3.9"))
    }

    @Test
    fun build_suffix_is_ignored() {
        assertFalse(isNewerVersion("3.9.1-beta", "3.9.1"))
        assertTrue(isNewerVersion("3.9.2-rc1", "3.9.1"))
    }

    @Test
    fun blank_candidate_is_never_newer() {
        assertFalse(isNewerVersion("", "3.9.1"))
        assertFalse(isNewerVersion("   ", "3.9.1"))
    }
}
