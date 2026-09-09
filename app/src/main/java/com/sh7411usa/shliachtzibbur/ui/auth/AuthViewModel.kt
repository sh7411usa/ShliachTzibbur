package com.sh7411usa.shliachtzibbur.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.AuthChallenge
import com.sh7411usa.shliachtzibbur.core.model.AuthMethod
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import com.sh7411usa.shliachtzibbur.data.repo.AuthRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val submitting: Boolean = false,
    val error: ApiException? = null,
    val challenge: AuthChallenge? = null,
    val phoneE164: String? = null,
    val resendInSeconds: Int = 0,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var lastCallingCode: String = ""
    private var lastNational: String = ""
    private var lastDisplayName: String = ""
    private var lastRegion: String? = null

    fun clearError() = _state.update { it.copy(error = null) }

    fun startSms(callingCode: String, national: String, displayName: String, region: String?) {
        val phone = PhoneNumbers.toE164(callingCode, national)
        if (!PhoneNumbers.looksValid(phone)) {
            _state.update {
                it.copy(error = ApiException("auth_error_invalid_phone", 0, null))
            }
            return
        }
        lastCallingCode = callingCode
        lastNational = national
        lastDisplayName = displayName
        lastRegion = region?.takeIf { it.isNotBlank() }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.start(AuthMethod.Sms, phone, displayName, lastRegion)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            submitting = false,
                            challenge = result.value,
                            phoneE164 = phone,
                        )
                    }
                    startResendCountdown(result.value.resendAfterSeconds)
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = result.error)
                }
            }
        }
    }

    fun resend() {
        val phone = _state.value.phoneE164 ?: return
        if (_state.value.resendInSeconds > 0) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.start(AuthMethod.Sms, phone, lastDisplayName, lastRegion)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, challenge = result.value) }
                    startResendCountdown(result.value.resendAfterSeconds)
                }

                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    fun verify(code: String) {
        val challenge = _state.value.challenge ?: return
        val phone = _state.value.phoneE164 ?: return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (
                val result = authRepository.verify(
                    method = AuthMethod.Sms,
                    challengeId = challenge.challengeId,
                    code = code,
                    phone = phone,
                    displayName = lastDisplayName,
                    region = lastRegion,
                )
            ) {
                is ApiResult.Success -> {
                    profileRepository.refresh()
                    _state.update { it.copy(submitting = false) }
                    // Session flow drives navigation to the main graph.
                }

                is ApiResult.Failure -> _state.update {
                    it.copy(submitting = false, error = result.error)
                }
            }
        }
    }

    private fun startResendCountdown(seconds: Int) {
        viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _state.update { it.copy(resendInSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _state.update { it.copy(resendInSeconds = 0) }
        }
    }
}
