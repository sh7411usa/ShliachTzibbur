package com.sh7411usa.shliachtzibbur.crypto

import com.sh7411usa.shliachtzibbur.core.crypto.Base64Codec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class Base64CodecTest {

    @Test
    fun `known vectors`() {
        assertEquals("", Base64Codec.encode(ByteArray(0)))
        assertEquals("Zg", Base64Codec.encode("f".toByteArray()))
        assertEquals("Zm8", Base64Codec.encode("fo".toByteArray()))
        assertEquals("Zm9v", Base64Codec.encode("foo".toByteArray()))
        assertEquals("Zm9vYmFy", Base64Codec.encode("foobar".toByteArray()))
    }

    @Test
    fun `round trips arbitrary bytes`() {
        repeat(200) {
            val bytes = Random.nextBytes(Random.nextInt(0, 300))
            val encoded = Base64Codec.encode(bytes)
            assertArrayEquals(bytes, Base64Codec.decode(encoded))
        }
    }

    @Test
    fun `tolerates padding and rejects junk`() {
        assertArrayEquals("foo".toByteArray(), Base64Codec.decode("Zm9v="))
        assertNull(Base64Codec.decode("not valid base64!!"))
        assertNull(Base64Codec.decode("A"))
    }
}
