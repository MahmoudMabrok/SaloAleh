package tools.mo3ta.salo.desktop

import java.io.File
import java.security.MessageDigest
import java.util.UUID

object DeviceId {
    private val configDir: File by lazy {
        File(System.getProperty("user.home"), ".saloaleh-desktop").apply { mkdirs() }
    }
    private val uidFile: File by lazy { File(configDir, "uid") }

    fun get(): String {
        if (uidFile.exists()) {
            val raw = uidFile.readText().trim()
            if (raw.isNotEmpty()) return sha256Hex(raw)
        }
        val fresh = UUID.randomUUID().toString()
        uidFile.writeText(fresh)
        return sha256Hex(fresh)
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
