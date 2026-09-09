package com.sh7411usa.shliachtzibbur.di

import android.content.Context
import com.sh7411usa.shliachtzibbur.BuildConfig
import com.sh7411usa.shliachtzibbur.core.net.NetworkFactory
import com.sh7411usa.shliachtzibbur.core.net.TokenProvider
import com.sh7411usa.shliachtzibbur.core.net.TzibburApi
import com.sh7411usa.shliachtzibbur.core.net.ws.TzibburWebSocket
import com.sh7411usa.shliachtzibbur.core.util.SmsCodeReceiver
import com.sh7411usa.shliachtzibbur.data.local.AppDatabase
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import com.sh7411usa.shliachtzibbur.data.repo.AuthRepository
import com.sh7411usa.shliachtzibbur.data.repo.ContactsRepository
import com.sh7411usa.shliachtzibbur.data.repo.GroupRepository
import com.sh7411usa.shliachtzibbur.data.repo.LegalRepository
import com.sh7411usa.shliachtzibbur.data.repo.MemberRepository
import com.sh7411usa.shliachtzibbur.data.repo.MessageRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import com.sh7411usa.shliachtzibbur.sync.NotificationHelper
import com.sh7411usa.shliachtzibbur.sync.SyncController
import com.sh7411usa.shliachtzibbur.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import com.sh7411usa.shliachtzibbur.core.model.Session
import java.util.concurrent.atomic.AtomicReference

/**
 * Manual dependency container, created once by [com.sh7411usa.shliachtzibbur.ShliachTzibburApp].
 * Everything is a lazily-initialised singleton. Kept deliberately simple so the
 * app avoids a DI framework dependency; can be split per-feature as it grows.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionStore = SessionStore(appContext)
    val settingsStore = SettingsStore(appContext)

    // Synchronous token snapshot for the OkHttp interceptor and WebSocket.
    private val tokenRef = AtomicReference<String?>(null)
    private val tokenProvider = TokenProvider { tokenRef.get() }

    /** Session as it stood at process start, for a flash-free first frame. */
    val initialSession: Session? = runBlocking { sessionStore.session.first() }

    init {
        tokenRef.set(initialSession?.token)
        sessionStore.session
            .onEach { tokenRef.set(it?.token) }
            .launchIn(appScope)
    }

    private val okHttpClient = NetworkFactory.okHttpClient(tokenProvider)
    private val httpEngine = NetworkFactory.httpEngine(BuildConfig.API_BASE_URL, okHttpClient)

    val api = TzibburApi(httpEngine)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val authRepository by lazy { AuthRepository(api, sessionStore, database) }
    val profileRepository by lazy { ProfileRepository(api) }
    val groupRepository by lazy { GroupRepository(api, database.groupDao(), settingsStore) }
    val messageRepository by lazy {
        MessageRepository(api, database.messageDao(), database.outboxDao(), database.groupDao())
    }
    val memberRepository by lazy { MemberRepository(api, database.memberDao()) }
    val legalRepository by lazy { LegalRepository(api) }
    val contactsRepository by lazy { ContactsRepository(appContext, api) }

    val smsCodeReceiver by lazy { SmsCodeReceiver(appContext) }

    val notificationHelper by lazy { NotificationHelper(appContext) }
    val syncController by lazy { SyncController(appContext) }

    val syncManager by lazy {
        SyncManager(
            api = api,
            messageRepository = messageRepository,
            groupRepository = groupRepository,
            groupDao = database.groupDao(),
            sessionStore = sessionStore,
            settingsStore = settingsStore,
            notifications = notificationHelper,
            webSocketFactory = { TzibburWebSocket(BuildConfig.WS_URL, okHttpClient, tokenProvider) },
        )
    }
}
