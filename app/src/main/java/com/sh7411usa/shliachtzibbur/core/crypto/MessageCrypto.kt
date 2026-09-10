package com.sh7411usa.shliachtzibbur.core.crypto

/** Result of trying to decrypt a message body. */
sealed interface CryptoOutcome {
    /** The text was never encrypted (no `$E<n>:` prefix). */
    data object Plain : CryptoOutcome

    /** Decrypted successfully with the key identified by [keyId]. */
    data class Decrypted(val plaintext: String, val keyId: String) : CryptoOutcome

    /** Looks encrypted but no known key / seq offset could open it. */
    data object Undecryptable : CryptoOutcome
}

/**
 * Front door to the encryption schemes. Register new schemes ([AesGcmSeqScheme]
 * today) in [schemes].
 */
object MessageCrypto {

    private val schemes: List<EncryptionScheme> = listOf(AesGcmSeqScheme)

    /** How far off the anticipated seq the real seq may be — the sender guesses `maxSeq + 1`. */
    private val SEQ_OFFSETS = listOf(0L, -1L, 1L, -2L, 2L, -3L, 3L)

    private val CIPHER_PREFIX = Regex("^\\\$E\\d+:")

    fun isCipherText(text: String): Boolean = CIPHER_PREFIX.containsMatchIn(text)

    private fun schemeFor(token: String): EncryptionScheme? =
        schemes.firstOrNull { token.startsWith(it.tokenPrefix) }

    /**
     * Tries to decrypt [text] for a message with actual sequence [actualSeq] from
     * [senderId]. [keys] should be ordered by the caller with the most likely key
     * first (active key, then newest → oldest).
     */
    fun decrypt(text: String, actualSeq: Long, senderId: String?, keys: List<GroupKey>): CryptoOutcome {
        if (!isCipherText(text)) return CryptoOutcome.Plain
        val scheme = schemeFor(text) ?: return CryptoOutcome.Undecryptable
        for (key in keys) {
            for (offset in SEQ_OFFSETS) {
                val seq = actualSeq + offset
                if (seq < 1) continue
                val plain = scheme.tryDecrypt(key.hex, seq, senderId, text)
                if (plain != null) return CryptoOutcome.Decrypted(plain, key.id)
            }
        }
        return CryptoOutcome.Undecryptable
    }

    /** Encrypts [plaintext] with [key] for the [anticipatedSeq] the send will land on. */
    fun encrypt(plaintext: String, anticipatedSeq: Long, senderId: String?, key: GroupKey): String =
        AesGcmSeqScheme.encrypt(key.hex, anticipatedSeq, senderId, plaintext)
}
