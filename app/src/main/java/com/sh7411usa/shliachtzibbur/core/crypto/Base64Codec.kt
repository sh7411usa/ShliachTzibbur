package com.sh7411usa.shliachtzibbur.core.crypto

/**
 * Minimal RFC 4648 Base64 (standard alphabet, no padding). Deliberately
 * hand-rolled and Android-free so the crypto package stays JVM-unit-testable
 * (`android.util.Base64` returns null under `unitTests.isReturnDefaultValues`,
 * and the desugared `java.util.Base64` isn't guaranteed on every variant).
 */
internal object Base64Codec {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val REVERSE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c -> table[c.code] = index }
    }

    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= bytes.size) {
            val n = (bytes[i].toInt() and 0xFF shl 16) or
                (bytes[i + 1].toInt() and 0xFF shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            out.append(ALPHABET[n ushr 18 and 0x3F])
            out.append(ALPHABET[n ushr 12 and 0x3F])
            out.append(ALPHABET[n ushr 6 and 0x3F])
            out.append(ALPHABET[n and 0x3F])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                out.append(ALPHABET[n ushr 18 and 0x3F])
                out.append(ALPHABET[n ushr 12 and 0x3F])
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                out.append(ALPHABET[n ushr 18 and 0x3F])
                out.append(ALPHABET[n ushr 12 and 0x3F])
                out.append(ALPHABET[n ushr 6 and 0x3F])
            }
        }
        return out.toString()
    }

    /** Returns null if [text] contains any character outside the alphabet or has an impossible length. */
    fun decode(text: String): ByteArray? {
        val clean = text.trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        if (clean.length % 4 == 1) return null
        val out = ByteArray(clean.length * 3 / 4)
        var outPos = 0
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = if (c.code < 128) REVERSE[c.code] else -1
            if (value < 0) return null
            buffer = buffer shl 6 or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[outPos++] = (buffer ushr bits and 0xFF).toByte()
            }
        }
        return if (outPos == out.size) out else out.copyOf(outPos)
    }
}
