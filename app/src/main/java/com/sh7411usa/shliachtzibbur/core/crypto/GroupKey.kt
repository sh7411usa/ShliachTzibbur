package com.sh7411usa.shliachtzibbur.core.crypto

import kotlinx.serialization.Serializable
import java.security.SecureRandom

/**
 * One AES-256 group key. [hex] is 64 lowercase hex characters (32 bytes).
 * [id] is a local identifier; [label] is shown in the key list.
 */
@Serializable
data class GroupKey(
    val id: String,
    val hex: String,
    val label: String,
    val addedAtMillis: Long,
)

/** Validation and generation for AES-256 keys in hex form. */
object KeyHex {

    private val PATTERN = Regex("^[0-9a-fA-F]{64}$")

    /** A standalone 64-hex run (not part of a longer hex string). */
    private val EMBEDDED = Regex("(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])")

    fun isValid(value: String): Boolean = PATTERN.matches(value.trim())

    /**
     * Pulls the key out of a larger blob — e.g. the whole key-hand-off SMS pasted
     * into the field — returning the first standalone 64-hex run, else null.
     */
    fun extract(text: String): String? = EMBEDDED.find(text)?.value?.lowercase()

    /** [text] reduced to just the key if one is embedded in it, otherwise trimmed as-is. */
    fun clean(text: String): String = extract(text) ?: text.trim()

    /** Normalises to lowercase, no surrounding whitespace. Caller must check [isValid] first. */
    fun normalize(value: String): String = value.trim().lowercase()

    /** A fresh random 256-bit key as 64 lowercase hex chars. */
    fun generate(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun toBytes(hex: String): ByteArray {
        val clean = normalize(hex)
        return ByteArray(clean.length / 2) { i ->
            ((clean[i * 2].digitToInt(16) shl 4) or clean[i * 2 + 1].digitToInt(16)).toByte()
        }
    }
}
