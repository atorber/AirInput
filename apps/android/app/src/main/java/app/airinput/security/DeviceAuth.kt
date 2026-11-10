package app.airinput.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Application-layer device auth (not a substitute for Bluetooth pairing).
 * Challenge -> HMAC-SHA256(response) -> AUTH_OK / AUTH_FAILED.
 */
object DeviceAuth {
    fun hmacSha256(pairingKey: ByteArray, challenge: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pairingKey, "HmacSHA256"))
        return mac.doFinal(challenge)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
