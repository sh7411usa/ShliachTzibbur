package com.sh7411usa.shliachtzibbur.core.model

/** Pending SMS challenge returned by `POST /v1/auth/start`. */
data class AuthChallenge(
    val challengeId: String,
    val resendAfterSeconds: Int,
)

/** Everything persisted after a successful `POST /v1/auth/verify`. */
data class Session(
    val token: String,
    val userId: String,
    val deviceId: String,
)

data class AuthResult(
    val session: Session,
    val user: User,
    val device: Device,
)

/**
 * Strategy for signing in. Only [Sms] is implemented; [Email] and [Google] are
 * declared so screens and the repository can branch on method without a rewrite
 * when the API gains support.
 */
sealed interface AuthMethod {
    data object Sms : AuthMethod
    data object Email : AuthMethod
    data object Google : AuthMethod

    companion object {
        val available: List<AuthMethod> = listOf(Sms)
    }
}
