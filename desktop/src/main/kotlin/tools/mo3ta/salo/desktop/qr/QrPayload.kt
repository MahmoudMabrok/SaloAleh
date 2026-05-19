package tools.mo3ta.salo.desktop.qr

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExtensionQrPayload(
    val v: Int = 1,
    val type: String = "saloaleh-submit",
    val uid: String = "",
    val round: String = "",
    val count: Int = 0,
    val country: String = "",
    val src: String = "desktop",
    val ts: Long = 0,
    val ttl: Long = 0,
    val sig: String = "",
)

private val json = Json { encodeDefaults = true }

const val QR_TTL_MS = 10L * 60_000L

fun buildSubmitPayload(uid: String, count: Int, country: String = ""): String {
    val now = Clock.System.now()
    val round = currentRoundKey(now)
    val ts = now.toEpochMilliseconds()
    val payload = ExtensionQrPayload(
        uid = uid,
        round = round,
        count = count,
        country = country,
        ts = ts,
        ttl = ts + QR_TTL_MS,
        sig = hmacSha256Hex("$round|$count"),
    )
    return json.encodeToString(ExtensionQrPayload.serializer(), payload)
}
