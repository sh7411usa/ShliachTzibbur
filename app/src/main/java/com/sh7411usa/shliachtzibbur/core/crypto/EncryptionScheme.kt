package com.sh7411usa.shliachtzibbur.core.crypto

/**
 * A message encryption scheme identified by a token prefix (`$E1:`, and room for
 * `$E2:` later). Keep implementations self-contained so a new scheme is a single
 * new file registered in [MessageCrypto].
 *
 * The plaintext handed to [encrypt] is the user's message. Schemes wrap it with a
 * known marker (`!`) so [tryDecrypt] can confirm a successful decryption; the
 * marker is stripped from [tryDecrypt]'s result.
 */
interface EncryptionScheme {

    /** Short id, e.g. `"E1"`. */
    val id: String

    /** Token prefix including the trailing colon, e.g. `"$E1:"`. */
    val tokenPrefix: String

    /**
     * Encrypts [plaintext] for message sequence [seq] sent by [senderId].
     * Returns a full token beginning with [tokenPrefix].
     */
    fun encrypt(keyHex: String, seq: Long, senderId: String?, plaintext: String): String

    /**
     * Attempts to decrypt [token] assuming it belongs to message sequence [seq]
     * from [senderId]. Returns the plaintext (marker already stripped) or null on
     * any failure — wrong key, wrong seq, tampering, or malformed input.
     */
    fun tryDecrypt(keyHex: String, seq: Long, senderId: String?, token: String): String?
}
