package tools.mo3ta.salo.data.crypto

private const val BLOCK_SIZE = 64
private const val HMAC_KEY = "s4l0-al3h-khr0m-2024-x7q9m2"

fun hmacSha256Hex(message: String): String {
    val keyBytes = HMAC_KEY.encodeToByteArray()
    val msgBytes = message.encodeToByteArray()

    val paddedKey = if (keyBytes.size > BLOCK_SIZE) {
        sha256Bytes(keyBytes)
    } else {
        keyBytes.copyOf(BLOCK_SIZE)
    }

    val iPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x36).toByte() }
    val oPad = ByteArray(BLOCK_SIZE) { (paddedKey[it].toInt() xor 0x5c).toByte() }

    val innerHash = sha256Bytes(iPad + msgBytes)
    val outerHash = sha256Bytes(oPad + innerHash)

    return outerHash.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
}
