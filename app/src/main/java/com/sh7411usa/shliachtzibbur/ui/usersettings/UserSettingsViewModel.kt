package com.sh7411usa.shliachtzibbur.ui.usersettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.LegalDocument
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.core.model.User
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import com.sh7411usa.shliachtzibbur.data.prefs.SessionStore
import com.sh7411usa.shliachtzibbur.data.repo.AuthRepository
import com.sh7411usa.shliachtzibbur.data.repo.LegalRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import com.sh7411usa.shliachtzibbur.sync.SyncController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserSettingsUiState(
    val user: User? = null,
    val savingName: Boolean = false,
    val error: ApiException? = null,
    val devices: List<Device>? = null,
    val devicesLoading: Boolean = false,
    val currentDeviceId: String? = null,
    /** True once the server has told us device removal isn't available. */
    val deviceRemovalUnsupported: Boolean = false,
    val legal: LegalDocument? = null,
    val legalLoading: Boolean = false,
)

class UserSettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val legalRepository: LegalRepository,
    private val authRepository: AuthRepository,
    private val syncController: SyncController,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UserSettingsUiState())
    val state: StateFlow<UserSettingsUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(user = profileRepository.user.value) }
        viewModelScope.launch {
            (profileRepository.refresh() as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(user = result.value) }
            }
        }
    }

    fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == _state.value.user?.displayName) return
        _state.update { it.copy(savingName = true, error = null) }
        viewModelScope.launch {
            when (val result = profileRepository.updateDisplayName(trimmed)) {
                is ApiResult.Success -> _state.update {
                    it.copy(savingName = false, user = result.value)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(savingName = false, error = result.error)
                }
            }
        }
    }

    fun loadDevices() {
        _state.update { it.copy(devicesLoading = true, error = null) }
        viewModelScope.launch {
            val currentId = sessionStore.session.first()?.deviceId
            when (val result = profileRepository.devices()) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        devicesLoading = false,
                        currentDeviceId = currentId,
                        devices = result.value.sortedWith(
                            compareByDescending<Device> { d -> d.id == currentId }
                                .thenByDescending { d -> d.lastSeenAt.orEmpty() },
                        ),
                    )
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(devicesLoading = false, error = result.error)
                }
            }
        }
    }

    fun removeDevice(deviceId: String) {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val result = profileRepository.removeDevice(deviceId)) {
                is ApiResult.Success -> loadDevices()
                is ApiResult.Failure -> {
                    val notSupported = result.error.status in setOf(404, 405, 501) ||
                        result.error.type == ErrorType.NOT_IMPLEMENTED ||
                        result.error.type == ErrorType.NOT_FOUND
                    _state.update {
                        if (notSupported) it.copy(deviceRemovalUnsupported = true)
                        else it.copy(error = result.error)
                    }
                }
            }
        }
    }

    fun loadLegal(kind: LegalKind) {
        _state.update { it.copy(legalLoading = true, legal = null) }
        viewModelScope.launch {
            when (val result = legalRepository.document(kind)) {
                is ApiResult.Success -> _state.update {
                    it.copy(legalLoading = false, legal = result.value)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(legalLoading = false, error = result.error)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            profileRepository.clear()
            syncController.apply(signedIn = false, serviceEnabled = false)
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
