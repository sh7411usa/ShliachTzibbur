package com.sh7411usa.shliachtzibbur.core.net

import com.sh7411usa.shliachtzibbur.core.model.AddMembersResult
import com.sh7411usa.shliachtzibbur.core.model.AuthChallenge
import com.sh7411usa.shliachtzibbur.core.model.AuthResult
import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.Group
import com.sh7411usa.shliachtzibbur.core.model.LegalDocument
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.core.model.Member
import com.sh7411usa.shliachtzibbur.core.model.Message
import com.sh7411usa.shliachtzibbur.core.model.MessagePage
import com.sh7411usa.shliachtzibbur.core.model.Page
import com.sh7411usa.shliachtzibbur.core.model.Role
import com.sh7411usa.shliachtzibbur.core.model.Session
import com.sh7411usa.shliachtzibbur.core.net.HttpEngine.Method
import com.sh7411usa.shliachtzibbur.core.net.dto.*
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Typed facade over every Tzibbur REST endpoint. Methods suspend, return domain
 * models, and throw [ApiException] on failure (wrap with `apiCatching` at the
 * repository layer to get an `ApiResult`).
 */
class TzibburApi(private val engine: HttpEngine) {

    private suspend inline fun <reified T> request(
        method: Method,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
    ): T {
        val text = engine.execute(method, path, query, bodyJson)
        if (text.isBlank()) {
            return try {
                NetJson.decodeFromString<T>("{}")
            } catch (e: Exception) {
                throw ApiException.parsing(e)
            }
        }
        return try {
            NetJson.decodeFromString<T>(text)
        } catch (e: Exception) {
            throw ApiException.parsing(e)
        }
    }

    private suspend fun requestUnit(
        method: Method,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
    ) {
        engine.execute(method, path, query, bodyJson)
    }

    // ---- Auth ----

    suspend fun startSms(
        phone: String,
        displayName: String?,
        region: String?,
        platform: String,
        deviceModel: String,
    ): AuthChallenge {
        val body = NetJson.encodeToString(
            AuthStartRequest(phone, displayName, region, platform, deviceModel)
        )
        return request<AuthStartResponse>(Method.POST, "/v1/auth/start", bodyJson = body).toDomain()
    }

    suspend fun verifySms(
        challengeId: String,
        code: String,
        phone: String,
        displayName: String?,
        region: String?,
        platform: String,
        deviceModel: String,
    ): AuthResult {
        val body = NetJson.encodeToString(
            AuthVerifyRequest(challengeId, code, phone, displayName, region, platform, deviceModel)
        )
        val dto = request<AuthVerifyResponse>(Method.POST, "/v1/auth/verify", bodyJson = body)
        return AuthResult(
            session = Session(token = dto.token, userId = dto.user.id, deviceId = dto.device.id),
            user = dto.user.toDomain(),
            device = dto.device.toDomain(),
        )
    }

    // ---- Profile ----

    suspend fun getMe() = request<UserDto>(Method.GET, "/v1/me").toDomain()

    suspend fun updateDisplayName(displayName: String) =
        request<UserDto>(
            Method.PATCH,
            "/v1/me",
            bodyJson = NetJson.encodeToString(UpdateMeRequest(displayName)),
        ).toDomain()

    suspend fun getDevices(): List<Device> =
        request<DeviceListDto>(Method.GET, "/v1/me/devices").items.map { it.toDomain() }

    // ---- Contacts ----

    suspend fun checkContacts(phones: List<String>, region: String?): List<String> =
        request<ContactsCheckResponse>(
            Method.POST,
            "/v1/contacts/check",
            bodyJson = NetJson.encodeToString(ContactsCheckRequest(phones, region)),
        ).registered

    // ---- Legal ----

    suspend fun getLegal(kind: LegalKind): LegalDocument =
        request<LegalResponse>(Method.GET, "/v1/legal/${kind.slug}").document.toDomain()

    // ---- Groups ----

    suspend fun listGroups(cursor: String?, limit: Int?): Page<Group> {
        val dto = request<GroupListDto>(
            Method.GET,
            "/v1/groups",
            query = mapOf("cursor" to cursor, "limit" to limit?.toString()),
        )
        return Page(dto.items.map { it.toDomain() }, dto.nextCursor)
    }

    suspend fun getCategories(): List<String> =
        request<GroupCategoriesDto>(Method.GET, "/v1/groups/categories").categories

    suspend fun createGroup(name: String, category: String): Group =
        request<GroupDto>(
            Method.POST,
            "/v1/groups",
            bodyJson = NetJson.encodeToString(CreateGroupRequest(name, category)),
        ).toDomain()

    suspend fun getGroup(id: String): Group =
        request<GroupDto>(Method.GET, "/v1/groups/$id").toDomain()

    suspend fun updateGroup(
        id: String,
        name: String?,
        whoCanPost: String?,
        whoCanAddMembers: String?,
    ): Group {
        val settings = if (whoCanPost != null || whoCanAddMembers != null) {
            GroupSettingsDto(whoCanPost, whoCanAddMembers)
        } else {
            null
        }
        val body = NetJson.encodeToString(UpdateGroupRequest(name, settings))
        return request<GroupDto>(Method.PATCH, "/v1/groups/$id", bodyJson = body).toDomain()
    }

    suspend fun deleteGroup(id: String) = requestUnit(Method.DELETE, "/v1/groups/$id")

    suspend fun leaveGroup(id: String) = requestUnit(Method.POST, "/v1/groups/$id/leave")

    // ---- Members ----

    suspend fun listMembers(groupId: String, cursor: String?, limit: Int?): Page<Member> {
        val dto = request<MemberListDto>(
            Method.GET,
            "/v1/groups/$groupId/members",
            query = mapOf("cursor" to cursor, "limit" to limit?.toString()),
        )
        return Page(dto.items.map { it.toDomain() }, dto.nextCursor)
    }

    suspend fun addMembers(groupId: String, phones: List<String>, region: String?): AddMembersResult =
        request<AddMembersResponseDto>(
            Method.POST,
            "/v1/groups/$groupId/members",
            bodyJson = NetJson.encodeToString(AddMembersRequest(phones, region)),
        ).toDomain()

    suspend fun changeRole(groupId: String, userId: String, role: Role): Unit =
        requestUnit(
            Method.PATCH,
            "/v1/groups/$groupId/members/$userId",
            bodyJson = NetJson.encodeToString(ChangeRoleRequest(role.wire)),
        )

    suspend fun removeMember(groupId: String, userId: String) =
        requestUnit(Method.DELETE, "/v1/groups/$groupId/members/$userId")

    // ---- Messages ----

    suspend fun getMessages(
        groupId: String,
        afterSeq: Long?,
        beforeSeq: Long?,
        limit: Int,
    ): MessagePage {
        val dto = request<MessageListDto>(
            Method.GET,
            "/v1/groups/$groupId/messages",
            query = mapOf(
                "afterSeq" to afterSeq?.toString(),
                "beforeSeq" to beforeSeq?.toString(),
                "limit" to limit.toString(),
            ),
        )
        return MessagePage(
            items = dto.items.map { it.toDomain(fallbackGroupId = groupId) },
            nextAfterSeq = dto.nextAfterSeq,
            nextBeforeSeq = dto.nextBeforeSeq,
        )
    }

    /**
     * Send a message. The reply "is not always a full message object" per the API,
     * so a null return means "accepted, reconcile by clientMessageId later".
     */
    suspend fun sendMessage(groupId: String, clientMessageId: String, body: String): Message? {
        val text = engine.execute(
            Method.POST,
            "/v1/groups/$groupId/messages",
            jsonBody = NetJson.encodeToString(SendMessageRequest(clientMessageId, body)),
        )
        if (text.isBlank()) return null
        return runCatching {
            NetJson.decodeFromString<MessageDto>(text).toDomain(fallbackGroupId = groupId)
        }.getOrNull()
    }

    suspend fun ack(groupId: String, seq: Long) =
        requestUnit(
            Method.POST,
            "/v1/groups/$groupId/ack",
            bodyJson = NetJson.encodeToString(AckRequest(seq)),
        )

    // ---- Delivery ----

    suspend fun getPending(): List<PendingGroupDto> =
        request<PendingResponse>(Method.GET, "/v1/pending").groups
}
