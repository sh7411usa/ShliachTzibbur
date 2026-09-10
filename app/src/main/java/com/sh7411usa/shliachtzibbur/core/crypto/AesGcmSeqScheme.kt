package com.sh7411usa.shliachtzibbur.core.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * `$E1` — AES-256-GCM with a deterministic nonce derived from the message
 * sequence number and sender id.
 *
 * Token layout: `"$E1:" + base64( ciphertext || 16-byte GCM tag )`.
 * Plaintext fed to GCM: `"!" + userText` (UTF-8). The leading `!` is the
 * known-plaintext marker that confirms a correct key/nonce.
 *
 * The nonce is not transmitted: the receiver reconstructs it from the message's
 * own seq and sender id (see [MessageCrypto] for the ±seq search that covers the
 * gap between the *anticipated* seq used when sending and the *actual* seq the
 * server assigned).
 *
 * Limitation: if the same sender encrypts two messages with the same anticipated
 * seq (e.g. two offline sends that race), the nonce repeats. GCM nonce reuse is
 * weak; [MessageCrypto] mitigates by spacing anticipated seqs across the outbox.
 */
object AesGcmSeqScheme : EncryptionScheme {

    override val id = "E1"
    override val tokenPrefix = "\$E1:"

    private const val TAG_BITS = 128
    private const val IV_LEN = 12
    private const val MARKER = '!'

    override fun encrypt(keyHex: String, seq: Long, senderId: String?, plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(KeyHex.toBytes(keyHex), "AES"),
            GCMParameterSpec(TAG_BITS, nonce(seq, senderId)),
        )
        val out = cipher.doFinal((MARKER + plaintext).toByteArray(Charsets.UTF_8))
        return tokenPrefix + Base64Codec.encode(out)
    }

    override fun tryDecrypt(keyHex: String, seq: Long, senderId: String?, token: String): String? {
        if (!token.startsWith(tokenPrefix)) return null
        val blob = Base64Codec.decode(token.substring(tokenPrefix.length)) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(KeyHex.toBytes(keyHex), "AES"),
                GCMParameterSpec(TAG_BITS, nonce(seq, senderId)),
            )
            val plain = cipher.doFinal(blob).toString(Charsets.UTF_8)
            if (plain.isNotEmpty() && plain[0] == MARKER) plain.substring(1) else null
        } catch (_: Exception) {
            // AEADBadTagException (wrong key/nonce/tampered), IllegalArgument, etc.
            null
        }
    }

    private fun nonce(seq: Long, senderId: String?): ByteArray {
        val material = "STZ/E1 $seq ${senderId.orEmpty()}".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(material).copyOf(IV_LEN)
    }
}
