package tools.mo3ta.salo.data.qr

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tools.mo3ta.salo.data.crypto.hmacSha256Hex

@Serializable
data class ExtensionQrPayload(
    val v: Int = 0,
    val type: String = "",
    val uid: String = "",
    val round: String = "",
    val count: Int = 0,
    val country: String = "",
    val src: String = "",
    val ts: Long = 0,
    val ttl: Long = 0,
    val sig: String = "",
)

sealed class QrVerifyResult {
    data class Success(val round: String, val count: Int) : QrVerifyResult()
    data class Error(val message: String, val isCheat: Boolean = false) : QrVerifyResult()
}

private val json = Json { ignoreUnknownKeys = true }

fun verifyExtensionQr(raw: String, nowMs: Long, lastAppliedTs: Long): QrVerifyResult {
    val payload = try {
        json.decodeFromString<ExtensionQrPayload>(raw)
    } catch (_: Exception) {
        return QrVerifyResult.Error("رمز غير صالح")
    }

    if (payload.type != "saloaleh-submit") {
        return QrVerifyResult.Error("رمز غير صالح")
    }

    if (payload.ttl < nowMs) {
        return QrVerifyResult.Error("انتهت صلاحية الرمز")
    }

    if (payload.ts <= lastAppliedTs) {
        return QrVerifyResult.Error("تم استخدام هذا الرمز مسبقًا")
    }

    if (payload.count <= 0) {
        return QrVerifyResult.Error("العدد صفر")
    }

    val expectedSig = hmacSha256Hex("${payload.round}|${payload.count}")
    if (!payload.sig.equals(expectedSig, ignoreCase = true)) {
        return QrVerifyResult.Error("توقيع غير صالح", isCheat = true)
    }

    return QrVerifyResult.Success(round = payload.round, count = payload.count)
}
