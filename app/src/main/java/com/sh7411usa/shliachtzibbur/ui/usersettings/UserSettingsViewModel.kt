package com.sh7411usa.shliachtzibbur.ui.usersettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.Device
import com.sh7411usa.shliachtzibbur.core.model.LegalDocument
import com.sh7411usa.shliachtzibbur.core.model.LegalKind
import com.sh7411usa.shliachtzibbur.core.model.User
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.data.repo.AuthRepository
import com.sh7411usa.shliachtzibbur.data.repo.LegalRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import com.sh7411usa.shliachtzibbur.sync.SyncController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserSettingsUiState(
    val user: User? = null,
    val savingName: Boolean = false,
    val error: ApiException? = null,
    val devices: List<Device>? = null,
    val devicesLoading: Boolean = false,
    val legal: LegalDocument? = null,
    val legalLoading: Boolean = false,
)

class UserSettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val legalRepository: LegalRepository,
    private val authRepository: AuthRepository,
    private val syncController: SyncController,
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
        if (_state.value.devicesLoading) return
        _state.update { it.copy(devicesLoading = true) }
        viewModelScope.launch {
            when (val result = profileRepository.devices()) {
                is ApiResult.Success -> _state.update {
                    it.copy(devicesLoading = false, devices = result.value)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(devicesLoading = false, error = result.error)
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
