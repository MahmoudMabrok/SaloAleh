package tools.mo3ta.salo.domain

/**
 * The account **backup code**: the raw UUID persisted as `user_uid`, from which the server
 * identity is derived as `sha256hex(raw)`.
 *
 * The code is the raw UUID and never the derived id, because the hash is one-way — the public
 * id shown on the leaderboard cannot be turned back into an identity. A user who saves this
 * code can re-adopt their account on a new device (see `AccountRestoreManager`); a user who
 * loses it has no recovery path, since the app has no accounts and no Firebase Auth.
 */
private val CANONICAL_CODE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

/** A UUID pasted with its dashes stripped — accepted and re-formatted rather than rejected. */
private val BARE_HEX_CODE = Regex("^[0-9a-f]{32}$")

/**
 * The *derived* id (`sha256hex` output). Recognised only so the restore screen can say "that is
 * your public id, not your backup code" instead of the generic invalid-code error — pasting it
 * would hash it a second time and silently adopt an empty account.
 */
private val PUBLIC_ID = Regex("^[0-9a-f]{64}$")

sealed interface BackupCodeParseResult {
    /** [code] is the canonical lowercase, hyphenated UUID — byte-identical to what was stored. */
    data class Valid(val code: String) : BackupCodeParseResult

    data object PublicId : BackupCodeParseResult

    data object Invalid : BackupCodeParseResult
}

/**
 * Parse user-pasted text into a backup code. Tolerates surrounding whitespace, internal spaces
 * and newlines (clipboards add them), uppercase, and missing dashes; everything else is invalid.
 */
fun parseBackupCode(input: String): BackupCodeParseResult {
    val cleaned = input.filterNot { it.isWhitespace() }.trim('"', '\'', '‏', '‎').lowercase()
    if (cleaned.isEmpty()) return BackupCodeParseResult.Invalid
    if (PUBLIC_ID.matches(cleaned)) return BackupCodeParseResult.PublicId
    if (CANONICAL_CODE.matches(cleaned)) return BackupCodeParseResult.Valid(cleaned)
    if (BARE_HEX_CODE.matches(cleaned)) return BackupCodeParseResult.Valid(withDashes(cleaned))
    return BackupCodeParseResult.Invalid
}

private fun withDashes(bare: String): String = buildString {
    append(bare, 0, 8); append('-')
    append(bare, 8, 12); append('-')
    append(bare, 12, 16); append('-')
    append(bare, 16, 20); append('-')
    append(bare, 20, 32)
}
