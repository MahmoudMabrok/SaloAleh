package tools.mo3ta.salo.desktop.qr

import java.security.MessageDigest

private const val HMAC_KEY = "s4l0-al3h-khr0m-2024-x7q9m2"
private const val BLOCK_SIZE = 64

fun hmacSha256Hex(message: String): String {
    val keyBytes = HMAC_KEY.toByteArray(Charsets.UTF_8)
    val msgBytes = message.toByteArray(Charsets.UTF_8)

    val paddedKey = if (keyBytes.size > BLOCK_SIZE) {
        sha256(keyBytes)
    } else {
        keyBytes.copyOf(BLOCK_SIZE)
    }

    val iPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x36).toByte() }
    val oPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x5c).toByte() }

    val inner = sha256(iPad + msgBytes)
    val outer = sha256(oPad + inner)

    return outer.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
}

private fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
