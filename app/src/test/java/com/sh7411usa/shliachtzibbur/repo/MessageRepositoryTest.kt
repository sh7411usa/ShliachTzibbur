package com.sh7411usa.shliachtzibbur.repo

import com.sh7411usa.shliachtzibbur.core.crypto.AesGcmSeqScheme
import com.sh7411usa.shliachtzibbur.core.crypto.GroupKey
import com.sh7411usa.shliachtzibbur.core.crypto.KeyHex
import com.sh7411usa.shliachtzibbur.core.model.ConversationItem
import com.sh7411usa.shliachtzibbur.core.model.MessageSecurity
import com.sh7411usa.shliachtzibbur.core.model.OutboxState
import com.sh7411usa.shliachtzibbur.core.net.HttpEngine
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.local.entity.GroupEntity
import com.sh7411usa.shliachtzibbur.data.local.entity.MessageEntity
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCrypto
import com.sh7411usa.shliachtzibbur.data.prefs.GroupCryptoSource
import com.sh7411usa.shliachtzibbur.data.prefs.NoEncryption
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import com.sh7411usa.shliachtzibbur.fakes.FakeGroupCryptoSource
import com.sh7411usa.shliachtzibbur.fakes.FakeGroupDao
import com.sh7411usa.shliachtzibbur.fakes.FakeMessageDao
import com.sh7411usa.shliachtzibbur.fakes.FakeOutboxDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MessageRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var messageDao: FakeMessageDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var groupDao: FakeGroupDao

    /** How the mock server should answer the POST /messages call. */
    private var postResponder: (clientMessageId: String) -> MockResponse = { fullMessage(it) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    request.method == "POST" && path.endsWith("/messages") -> {
                        val body = request.body.readUtf8()
                        lastPostBody = body
                        val cmid = Regex("\"clientMessageId\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1).orEmpty()
                        lastClientMessageId = cmid
                        postResponder(cmid)
                    }
                    request.method == "GET" && path.contains("/messages") -> {
                        val cmid = lastClientMessageId
                        if (cmid == null) json("""{"items":[]}""")
                        else json("""{"items":[${messageJson(cmid)}]}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("{}")
                }
            }
        }
        server.start()

        messageDao = FakeMessageDao()
        outboxDao = FakeOutboxDao()
        groupDao = FakeGroupDao()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private var lastClientMessageId: String? = null
    private var lastPostBody: String? = null

    private fun repository(
        timeoutMs: Long = 5_000L,
        crypto: GroupCryptoSource = NoEncryption,
        selfUserId: String? = "u1",
    ): MessageRepository {
        val client = OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val api = TzibburApi(HttpEngine(server.url("/").toString(), client))
        groupDao.rows.value = listOf(group("g1"))
        return MessageRepository(
            api, messageDao, outboxDao, groupDao,
            crypto = crypto,
            selfUserId = { selfUserId },
            sendTimeoutMs = timeoutMs,
        )
    }

    @Test
    fun `full message reply confirms and clears the outbox`() = runBlocking {
        postResponder = { cmid -> fullMessage(cmid) }
        val repo = repository()

        val result = repo.send("g1", "hello")

        assertTrue(result is ApiResult.Success)
        assertNull("outbox row should be gone", outboxDao.find(lastClientMessageId!!))
        assertEquals(1, messageDao.rows.value.size)
    }

    @Test
    fun `empty ack reply is reconciled via refreshLatest`() = runBlocking {
        postResponder = { json("""{"ok":true}""") }
        val repo = repository()

        val result = repo.send("g1", "hi")

        assertTrue(result is ApiResult.Success)
        assertNull(outboxDao.find(lastClientMessageId!!))
        assertEquals(1, messageDao.rows.value.size)
    }

    @Test
    fun `server rejection marks the row FAILED`() = runBlocking {
        postResponder = {
            MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"type":"urn:tzibbur:error:group_too_small","status":409,"detail":"too small"}""")
        }
        val repo = repository()

        val result = repo.send("g1", "hi")

        assertTrue(result is ApiResult.Failure)
        val row = outboxDao.find(lastClientMessageId!!)!!
        assertEquals(OutboxState.FAILED.name, row.state)
        assertTrue(row.lastError!!.contains("group_too_small"))
    }

    @Test
    fun `a hung send times out and is marked FAILED`() = runBlocking {
        postResponder = { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val repo = repository(timeoutMs = 300L)

        val result = repo.send("g1", "hi")

        assertTrue(result is ApiResult.Failure)
        val row = outboxDao.find(lastClientMessageId!!)!!
        assertEquals(OutboxState.FAILED.name, row.state)
    }

    @Test
    fun `deleteOutbox removes a failed row`() = runBlocking {
        postResponder = { MockResponse().setResponseCode(500).setBody("{}") }
        val repo = repository()
        repo.send("g1", "hi")
        val cmid = lastClientMessageId!!
        assertEquals(OutboxState.FAILED.name, outboxDao.find(cmid)!!.state)

        repo.deleteOutbox(cmid)

        assertNull(outboxDao.find(cmid))
    }

    @Test
    fun `encrypted group sends ciphertext, not plaintext`() = runBlocking {
        postResponder = { json("""{"ok":true}""") }
        val hex = KeyHex.generate()
        val crypto = FakeGroupCryptoSource(
            GroupCrypto(enabled = true, keys = listOf(GroupKey("k", hex, "k", 0)), activeKeyId = "k"),
        )
        val repo = repository(crypto = crypto)

        repo.send("g1", "hello secret")

        assertTrue("body carries the \$E1 token", lastPostBody!!.contains("\$E1:"))
        assertTrue("plaintext is not on the wire", !lastPostBody!!.contains("hello secret"))
    }

    @Test
    fun `locked encrypted group refuses to send`() = runBlocking {
        val crypto = FakeGroupCryptoSource(GroupCrypto(enabled = true))
        val repo = repository(crypto = crypto)

        val result = repo.send("g1", "hi")

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `incoming ciphertext decrypts to Secure`() = runBlocking {
        val hex = KeyHex.generate()
        val crypto = FakeGroupCryptoSource(
            GroupCrypto(enabled = true, keys = listOf(GroupKey("k", hex, "k", 0)), activeKeyId = "k"),
        )
        val token = AesGcmSeqScheme.encrypt(hex, 4, "u2", "hi there")
        messageDao.rows.value = listOf(
            MessageEntity("m1", "g1", 4, "u2", "Bob: $token", null, null),
        )
        val repo = repository(crypto = crypto)

        val delivered = repo.conversation("g1").first()
            .filterIsInstance<ConversationItem.Delivered>().single()

        assertEquals(MessageSecurity.Secure, delivered.security)
        assertEquals("hi there", delivered.plaintext)
        assertEquals("hi there", delivered.displayText)
    }

    @Test
    fun `plaintext in an enabled group is flagged Insecure`() = runBlocking {
        val crypto = FakeGroupCryptoSource(GroupCrypto(enabled = true, enabledSinceSeq = 1))
        messageDao.rows.value = listOf(
            MessageEntity("m2", "g1", 5, "u2", "Bob: plain talk", null, null),
        )
        val repo = repository(crypto = crypto)

        val delivered = repo.conversation("g1").first()
            .filterIsInstance<ConversationItem.Delivered>().single()

        assertEquals(MessageSecurity.Insecure, delivered.security)
    }

    // --- helpers ---

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fullMessage(cmid: String) = json(messageJson(cmid))

    private fun messageJson(cmid: String, seq: Int = 1) =
        """{"id":"m-$cmid","groupId":"g1","seq":$seq,"senderId":"u1","body":"Me: hi","clientMessageId":"$cmid","createdAt":"2026-09-09T00:00:00Z"}"""

    private fun group(id: String) = GroupEntity(
        id = id, name = "G", category = "family", kind = "standard", createdBy = null, createdAt = null,
        role = "admin", memberCount = 5, readSeq = 0, unreadCountHint = 0,
        whoCanPost = "everyone", whoCanAddMembers = "admins",
        memberCap = 100, messageMaxLength = 1000, minMembersToPost = 3,
    )
}
