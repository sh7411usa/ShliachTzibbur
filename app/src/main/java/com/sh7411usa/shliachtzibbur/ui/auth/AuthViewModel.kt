package com.sh7411usa.shliachtzibbur.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sh7411usa.shliachtzibbur.core.model.AuthChallenge
import com.sh7411usa.shliachtzibbur.core.model.AuthMethod
import com.sh7411usa.shliachtzibbur.core.result.ApiException
import com.sh7411usa.shliachtzibbur.core.result.ApiResult
import com.sh7411usa.shliachtzibbur.core.result.ErrorType
import com.sh7411usa.shliachtzibbur.core.util.PhoneDetection
import com.sh7411usa.shliachtzibbur.core.util.PhoneNumbers
import com.sh7411usa.shliachtzibbur.core.util.SimOption
import com.sh7411usa.shliachtzibbur.core.util.SmsCodeReceiver
import com.sh7411usa.shliachtzibbur.data.prefs.SettingsStore
import com.sh7411usa.shliachtzibbur.data.repo.AuthRepository
import com.sh7411usa.shliachtzibbur.data.repo.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val submitting: Boolean = false,
    val error: ApiException? = null,
    val challenge: AuthChallenge? = null,
    /** The E.164 number a code was sent to. */
    val phoneE164: String? = null,
    val resendInSeconds: Int = 0,
    // ---- phone entry ----
    /** Prefill for the number field (last used, or a single SIM's number). */
    val phonePrefill: String = "",
    /** Non-null when multiple SIMs have numbers — the user must choose. */
    val simChoices: List<SimOption>? = null,
    /** True when we still need the phone-number permission to look for the SIM number. */
    val needsPhonePermission: Boolean = false,
    /** True after the server rejects registration for a missing display name. */
    val needsNickname: Boolean = false,
    // ---- code entry ----
    val autoDetecting: Boolean = false,
    /** Set when the code was auto-detected from an SMS, so the field can show it. */
    val detectedCode: String? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val settingsStore: SettingsStore,
    private val smsCodeReceiver: SmsCodeReceiver,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var lastPhone: String = ""
    private var lastRegion: String? = null
    private var lastNickname: String? = null
    private var autoDetectJob: Job? = null

    init {
        viewModelScope.launch {
            val last = settingsStore.settings.first().lastPhoneE164
            if (last.isNotBlank()) _state.update { it.copy(phonePrefill = last) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------------------------------------------------------------- phone step

    /** Called by the screen after running SIM detection (only when no prefill yet). */
    fun onPhoneDetection(detection: PhoneDetection) {
        if (_state.value.phonePrefill.isNotBlank()) return
        _state.update {
            when (detection) {
                is PhoneDetection.Prefill -> it.copy(phonePrefill = detection.e164, needsPhonePermission = false)
                is PhoneDetection.ChooseSim -> it.copy(simChoices = detection.options, needsPhonePermission = false)
                PhoneDetection.NeedsPermission -> it.copy(needsPhonePermission = true)
                PhoneDetection.None -> it.copy(needsPhonePermission = false)
            }
        }
    }

    fun chooseSim(option: SimOption?) {
        _state.update { it.copy(simChoices = null, phonePrefill = option?.e164 ?: "") }
    }

    /**
     * Attempt `POST /v1/auth/start`. First call passes [nickname] = null; if the
     * server needs a display name for a new registration, [AuthUiState.needsNickname]
     * flips on and the screen re-submits with a nickname and the same number.
     */
    fun submitPhone(phoneRaw: String, region: String?, nickname: String?) {
        val phone = normalise(phoneRaw)
        if (!PhoneNumbers.looksValid(phone)) {
            _state.update { it.copy(error = ApiException(ERR_INVALID_PHONE, 0, null)) }
            return
        }
        lastPhone = phone
        lastRegion = region?.takeIf { it.isNotBlank() }
        lastNickname = nickname?.takeIf { it.isNotBlank() }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.start(AuthMethod.Sms, phone, lastNickname, lastRegion)) {
                is ApiResult.Success -> {
                    settingsStore.setLastPhoneE164(phone)
                    _state.update {
                        it.copy(
                            submitting = false,
                            challenge = result.value,
                            phoneE164 = phone,
                            needsNickname = false,
                        )
                    }
                    startResendCountdown(result.value.resendAfterSeconds)
                }

                is ApiResult.Failure -> {
                    if (needsDisplayName(result.error) && lastNickname == null) {
                        _state.update { it.copy(submitting = false, needsNickname = true, error = null) }
                    } else {
                        _state.update { it.copy(submitting = false, error = result.error) }
                    }
                }
            }
        }
    }


    fun resend() {
        val phone = _state.value.phoneE164 ?: return
        if (_state.value.resendInSeconds > 0) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.start(AuthMethod.Sms, phone, lastNickname, lastRegion)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, challenge = result.value) }
                    startResendCountdown(result.value.resendAfterSeconds)
                }

                is ApiResult.Failure -> _state.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    // ----------------------------------------------------------------- code step

    fun startSmsAutoDetect() {
        if (autoDetectJob?.isActive == true) return
        if (!smsCodeReceiver.hasAnyPermission()) return
        _state.update { it.copy(autoDetecting = true) }
        autoDetectJob = viewModelScope.launch {
            val code = smsCodeReceiver.awaitCode(timeoutMs = 90_000L)
            // The screen observes detectedCode, fills the field, and submits.
            _state.update { it.copy(autoDetecting = false, detectedCode = code) }
        }
    }

    fun stopSmsAutoDetect() {
        autoDetectJob?.cancel()
        autoDetectJob = null
        _state.update { it.copy(autoDetecting = false) }
    }

    fun verify(code: String, nickname: String? = null) {
        val challenge = _state.value.challenge ?: return
        val phone = _state.value.phoneE164 ?: return
        nickname?.takeIf { it.isNotBlank() }?.let { lastNickname = it }
        stopSmsAutoDetect()
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = authRepository.verify(
                method = AuthMethod.Sms,
                challengeId = challenge.challengeId,
                code = code,
                phone = phone,
                displayName = lastNickname,
                region = lastRegion,
            )
            when (result) {
                is ApiResult.Success -> {
                    profileRepository.refresh()
                    _state.update { it.copy(submitting = false) }
                }

                is ApiResult.Failure -> {
                    // New registration: the server needs a display name. Reveal
                    // the field on the code screen and let the user retry the
                    // same code with a name.
                    if (needsDisplayName(result.error) && lastNickname == null) {
                        _state.update { it.copy(submitting = false, needsNickname = true, error = null) }
                    } else {
                        _state.update { it.copy(submitting = false, error = result.error, detectedCode = null) }
                    }
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

    private fun normalise(input: String): String = "+" + input.filter { it.isDigit() }

    override fun onCleared() {
        autoDetectJob?.cancel()
    }

    companion object {
        const val ERR_INVALID_PHONE = "auth_error_invalid_phone"
    }
}

/**
 * True when the server rejected `start` only because a new registration needs a
 * display name (e.g. "A display name is required to create an account"). The
 * exact error shape isn't documented, so this matches the known slugs and, as a
 * fallback, any "display name" text in the type / detail / field errors.
 */
internal fun needsDisplayName(error: ApiException): Boolean {
    if (error.type == ErrorType.INVALID_DISPLAY_NAME || error.type == ErrorType.RESERVED_DISPLAY_NAME) {
        return true
    }
    val haystack = buildString {
        append(error.type).append(' ')
        append(error.detail.orEmpty()).append(' ')
        error.fieldErrors.forEach { (k, v) -> append(k).append(' ').append(v).append(' ') }
    }.lowercase()
    return "display name" in haystack ||
        "displayname" in haystack ||
        "display_name" in haystack ||
        ("name" in haystack && ("required" in haystack || "missing" in haystack))
}
