package tools.mo3ta.salo.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountBackupCodeTest {

    private val code = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    @Test
    fun canonicalCode_isAccepted() {
        assertEquals(BackupCodeParseResult.Valid(code), parseBackupCode(code))
    }

    @Test
    fun uppercaseAndSurroundingWhitespace_areNormalised() {
        assertEquals(
            BackupCodeParseResult.Valid(code),
            parseBackupCode("  ${code.uppercase()}\n"),
        )
    }

    @Test
    fun internalWhitespaceFromClipboard_isStripped() {
        assertEquals(
            BackupCodeParseResult.Valid(code),
            parseBackupCode("3f2504e0-4f89-41d3 -9a0c-\n0305e82c3301"),
        )
    }

    @Test
    fun codeWithoutDashes_isReformatted() {
        assertEquals(
            BackupCodeParseResult.Valid(code),
            parseBackupCode(code.replace("-", "")),
        )
    }

    @Test
    fun hashedPublicId_isReportedSeparately() {
        // A sha256 hex digest: pasting it would be hashed a second time and adopt nothing.
        assertEquals(BackupCodeParseResult.PublicId, parseBackupCode("a".repeat(64)))
    }

    @Test
    fun blankAndGarbage_areInvalid() {
        assertEquals(BackupCodeParseResult.Invalid, parseBackupCode(""))
        assertEquals(BackupCodeParseResult.Invalid, parseBackupCode("   "))
        assertEquals(BackupCodeParseResult.Invalid, parseBackupCode("my account please"))
        // Right length, but not hex.
        assertEquals(
            BackupCodeParseResult.Invalid,
            parseBackupCode("zf2504e0-4f89-41d3-9a0c-0305e82c3301"),
        )
        // One character short.
        assertEquals(BackupCodeParseResult.Invalid, parseBackupCode(code.dropLast(1)))
    }
}
