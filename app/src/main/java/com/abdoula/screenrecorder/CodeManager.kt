package com.abdoula.screenrecorder

import android.content.Context
import kotlin.random.Random

// Système de codes d'activation Pro, entièrement hors-ligne.
// Le code encode : une date d'expiration (délai avant lequel il doit être
// utilisé) et une durée de Pro accordée une fois activé. Un contrôle simple
// empêche de réutiliser deux fois le même code sur le même appareil.
object CodeManager {

    private const val PREFS = "app_settings"
    private const val KEY_CODE_EXPIRY = "code_pro_expiry"
    private const val KEY_USED_CODES = "used_activation_codes"

    // Change cette valeur si tu veux invalider tous les anciens codes générés
    private const val SECRET_BYTE = 0x5A

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Base32 Crockford (sans I, L, O, U)

    sealed class RedeemResult {
        data class Success(val durationDays: Int) : RedeemResult()
        object InvalidFormat : RedeemResult()
        object Expired : RedeemResult()
        object AlreadyUsed : RedeemResult()
    }

    // durationDays = combien de jours de Pro le code donne une fois activé
    // validForDays = combien de jours ce code reste valable AVANT activation
    fun generateCode(durationDays: Int, validForDays: Int): String {
        val nowDay = (System.currentTimeMillis() / 86_400_000L).toInt()
        val expiryDay = nowDay + validForDays
        val random = Random.nextInt(0, 65536)

        val bytes = ByteArray(7)
        bytes[0] = ((expiryDay shr 8) and 0xFF).toByte()
        bytes[1] = (expiryDay and 0xFF).toByte()
        bytes[2] = ((durationDays shr 8) and 0xFF).toByte()
        bytes[3] = (durationDays and 0xFF).toByte()
        bytes[4] = ((random shr 8) and 0xFF).toByte()
        bytes[5] = (random and 0xFF).toByte()

        var sum = 0
        for (i in 0..5) sum += (bytes[i].toInt() and 0xFF)
        bytes[6] = ((sum xor SECRET_BYTE) and 0xFF).toByte()

        return bytesToBase32(bytes).chunked(4).joinToString("-")
    }

    fun redeem(context: Context, rawCode: String): RedeemResult {
        val cleaned = rawCode.replace("-", "").replace(" ", "").uppercase()
        val bytes = base32ToBytes(cleaned, 7) ?: return RedeemResult.InvalidFormat

        var sum = 0
        for (i in 0..5) sum += (bytes[i].toInt() and 0xFF)
        val expectedChecksum = (sum xor SECRET_BYTE) and 0xFF
        val actualChecksum = bytes[6].toInt() and 0xFF
        if (expectedChecksum != actualChecksum) return RedeemResult.InvalidFormat

        val expiryDay = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        val durationDays = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        val nowDay = (System.currentTimeMillis() / 86_400_000L).toInt()
        if (nowDay > expiryDay) return RedeemResult.Expired

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val usedCodes = prefs.getStringSet(KEY_USED_CODES, emptySet()) ?: emptySet()
        if (usedCodes.contains(cleaned)) return RedeemResult.AlreadyUsed

        val currentExpiry = prefs.getLong(KEY_CODE_EXPIRY, 0L)
        val base = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
        val newExpiry = base + durationDays * 86_400_000L

        val newUsedCodes = HashSet(usedCodes)
        newUsedCodes.add(cleaned)

        prefs.edit()
            .putLong(KEY_CODE_EXPIRY, newExpiry)
            .putStringSet(KEY_USED_CODES, newUsedCodes)
            .apply()

        return RedeemResult.Success(durationDays)
    }

    fun isCodeProActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CODE_EXPIRY, 0L) > System.currentTimeMillis()
    }

    fun getRemainingDays(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_CODE_EXPIRY, 0L)
        val remaining = expiry - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 86_400_000L) + 1 else 0L
    }

    private fun bytesToBase32(bytes: ByteArray): String {
        var buffer = 0L
        var bitsLeft = 0
        val sb = StringBuilder()
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                val index = ((buffer shr bitsLeft) and 0x1F).toInt()
                sb.append(ALPHABET[index])
            }
        }
        if (bitsLeft > 0) {
            val index = ((buffer shl (5 - bitsLeft)) and 0x1F).toInt()
            sb.append(ALPHABET[index])
        }
        return sb.toString()
    }

    private fun base32ToBytes(str: String, byteCount: Int): ByteArray? {
        var buffer = 0L
        var bitsLeft = 0
        val out = ArrayList<Byte>()
        for (c in str) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return if (out.size >= byteCount) out.take(byteCount).toByteArray() else null
    }
}