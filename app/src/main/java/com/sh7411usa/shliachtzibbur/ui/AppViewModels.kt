package com.sh7411usa.shliachtzibbur.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sh7411usa.shliachtzibbur.ShliachTzibburApp
import com.sh7411usa.shliachtzibbur.di.AppContainer
import com.sh7411usa.shliachtzibbur.ui.appsettings.AppSettingsViewModel
import com.sh7411usa.shliachtzibbur.ui.auth.AuthViewModel
import com.sh7411usa.shliachtzibbur.ui.contacts.ContactsViewModel
import com.sh7411usa.shliachtzibbur.ui.groups.CreateGroupViewModel
import com.sh7411usa.shliachtzibbur.ui.groups.GroupsViewModel
import com.sh7411usa.shliachtzibbur.ui.groupsettings.GroupSettingsViewModel
import com.sh7411usa.shliachtzibbur.ui.groupsettings.MembersViewModel
import com.sh7411usa.shliachtzibbur.ui.messages.MessagesViewModel
import com.sh7411usa.shliachtzibbur.ui.usersettings.UserSettingsViewModel

/** [CreationExtras] -> [AppContainer] shortcut for ViewModel initializers. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as ShliachTzibburApp).container

/** Route arg keys shared between the nav graph and ViewModels' SavedStateHandle. */
object NavArg {
    const val GROUP_ID = "groupId"
}

/**
 * One factory for every ViewModel in the app. Manual wiring keeps the DI story
 * simple; each entry pulls what it needs from [AppContainer] (and the
 * SavedStateHandle for route args).
 */
object AppViewModelFactory {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AuthViewModel(
                authRepository = container.authRepository,
                profileRepository = container.profileRepository,
                settingsStore = container.settingsStore,
                smsCodeReceiver = container.smsCodeReceiver,
            )
        }
        initializer { GroupsViewModel(container.groupRepository) }
        initializer {
            CreateGroupViewModel(
                savedStateHandle = createSavedStateHandle(),
                groupRepository = container.groupRepository,
                memberRepository = container.memberRepository,
            )
        }
        initializer {
            ContactsViewModel(
                contactsRepository = container.contactsRepository,
                groupRepository = container.groupRepository,
                memberRepository = container.memberRepository,
            )
        }
        initializer {
            MessagesViewModel(
                savedStateHandle = createSavedStateHandle(),
                groupRepository = container.groupRepository,
                messageRepository = container.messageRepository,
                profileRepository = container.profileRepository,
                syncManager = container.syncManager,
                sessionStore = container.sessionStore,
            )
        }
        initializer {
            GroupSettingsViewModel(
                savedStateHandle = createSavedStateHandle(),
                groupRepository = container.groupRepository,
            )
        }
        initializer {
            MembersViewModel(
                savedStateHandle = createSavedStateHandle(),
                memberRepository = container.memberRepository,
                groupRepository = container.groupRepository,
                sessionStore = container.sessionStore,
            )
        }
        initializer {
            UserSettingsViewModel(
                profileRepository = container.profileRepository,
                legalRepository = container.legalRepository,
                authRepository = container.authRepository,
                syncController = container.syncController,
            )
        }
        initializer {
            AppSettingsViewModel(
                settingsStore = container.settingsStore,
            )
        }
    }
}
