package com.issaczerubbabel.ledgar.util

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinSecurity {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH_BYTES = 16

    fun generateSaltBase64(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPinBase64(rawPin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        return deriveHashBase64(rawPin, salt)
    }

    fun verifyPin(rawPin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val derived = hashPinBase64(rawPin, saltBase64)
        return constantTimeEquals(derived, expectedHashBase64)
    }

    private fun deriveHashBase64(rawPin: String, salt: ByteArray): String {
        val chars = rawPin.toCharArray()
        return try {
            val spec: KeySpec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hashBytes = skf.generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(hashBytes)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
