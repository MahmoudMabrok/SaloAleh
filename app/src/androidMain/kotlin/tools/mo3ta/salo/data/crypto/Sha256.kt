package tools.mo3ta.salo.data.crypto

import java.security.MessageDigest

actual fun sha256hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
