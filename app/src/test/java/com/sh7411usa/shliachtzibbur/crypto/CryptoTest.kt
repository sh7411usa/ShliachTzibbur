package com.sh7411usa.shliachtzibbur.crypto

import com.sh7411usa.shliachtzibbur.core.crypto.AesGcmSeqScheme
import com.sh7411usa.shliachtzibbur.core.crypto.CryptoOutcome
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.crypto.MessageCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHexTest {

    @Test
    fun `validates 64 hex chars`() {
        assertTrue(KeyHex.isValid("a".repeat(64)))
        assertTrue(KeyHex.isValid(("ABCDEF0123456789").repeat(4)))
        assertFalse(KeyHex.isValid("a".repeat(63)))
        assertFalse(KeyHex.isValid("a".repeat(65)))
        assertFalse(KeyHex.isValid("g".repeat(64)))
    }

    @Test
    fun `generate produces a distinct valid key each call`() {
        val a = KeyHex.generate()
        val b = KeyHex.generate()
        assertTrue(KeyHex.isValid(a))
        assertEquals(64, a.length)
        assertNotEquals(a, b)
    }

    @Test
    fun `toBytes round-trips generated keys`() {
        val hex = KeyHex.generate()
        assertEquals(32, KeyHex.toBytes(hex).size)
    }
}

class AesGcmSchemeTest {

    private val key = KeyHex.generate()

    @Test
    fun `round trips a message`() {
        val token = AesGcmSeqScheme.encrypt(key, seq = 5, senderId = "u1", plaintext = "hello world")
        assertTrue(token.startsWith("\$E1:"))
        assertEquals("hello world", AesGcmSeqScheme.tryDecrypt(key, 5, "u1", token))
    }

    @Test
    fun `wrong key fails`() {
        val token = AesGcmSeqScheme.encrypt(key, 5, "u1", "secret")
        assertNull(AesGcmSeqScheme.tryDecrypt(KeyHex.generate(), 5, "u1", token))
    }

    @Test
    fun `wrong seq or sender fails`() {
        val token = AesGcmSeqScheme.encrypt(key, 5, "u1", "secret")
        assertNull(AesGcmSeqScheme.tryDecrypt(key, 6, "u1", token))
        assertNull(AesGcmSeqScheme.tryDecrypt(key, 5, "u2", token))
    }

    @Test
    fun `tampered ciphertext fails`() {
        val token = AesGcmSeqScheme.encrypt(key, 5, "u1", "secret")
        val flipped = token.dropLast(2) + if (token.last() == 'A') "BB" else "AA"
        assertNull(AesGcmSeqScheme.tryDecrypt(key, 5, "u1", flipped))
    }

    @Test
    fun `nonce is deterministic for a seq and sender`() {
        val a = AesGcmSeqScheme.encrypt(key, 9, "u1", "x")
        val b = AesGcmSeqScheme.encrypt(key, 9, "u1", "x")
        assertEquals(a, b)
    }
}

class MessageCryptoTest {

    private val key = GroupKey("k1", KeyHex.generate(), "k", 0)
    private val old = GroupKey("k0", KeyHex.generate(), "old", 0)

    @Test
    fun `plain text passes through`() {
        assertFalse(MessageCrypto.isCipherText("just a message"))
        assertEquals(CryptoOutcome.Plain, MessageCrypto.decrypt("hi", 1, "u1", listOf(key)))
    }

    @Test
    fun `decrypts within the seq search window`() {
        // Sent anticipating seq 10, actually landed at 12 (delta +2 from actual view of 10..).
        val token = AesGcmSeqScheme.encrypt(key.hex, 10, "u1", "hey")
        assertTrue(MessageCrypto.decrypt(token, 12, "u1", listOf(key)) is CryptoOutcome.Decrypted)
        assertTrue(MessageCrypto.decrypt(token, 13, "u1", listOf(key)) is CryptoOutcome.Decrypted)
    }

    @Test
    fun `gives up beyond the window`() {
        val token = AesGcmSeqScheme.encrypt(key.hex, 10, "u1", "hey")
        assertEquals(CryptoOutcome.Undecryptable, MessageCrypto.decrypt(token, 20, "u1", listOf(key)))
    }

    @Test
    fun `falls back to an older key`() {
        val token = AesGcmSeqScheme.encrypt(old.hex, 3, "u1", "history")
        val outcome = MessageCrypto.decrypt(token, 3, "u1", listOf(key, old))
        assertTrue(outcome is CryptoOutcome.Decrypted)
        assertEquals("history", (outcome as CryptoOutcome.Decrypted).plaintext)
        assertEquals("k0", outcome.keyId)
    }

    @Test
    fun `undecryptable with no keys`() {
        val token = AesGcmSeqScheme.encrypt(key.hex, 3, "u1", "x")
        assertEquals(CryptoOutcome.Undecryptable, MessageCrypto.decrypt(token, 3, "u1", emptyList()))
    }

    @Test
    fun `projectedCipherLength matches the real token length`() {
        listOf("", "hi", "a".repeat(300), "über cool ☕ message with emoji 🎉", "x".repeat(700)).forEach { pt ->
            assertEquals(
                pt,
                AesGcmSeqScheme.encrypt(key.hex, 7, "u1", pt).length,
                MessageCrypto.projectedCipherLength(pt),
            )
        }
    }
}
