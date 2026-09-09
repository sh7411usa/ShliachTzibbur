package com.sh7411usa.shliachtzibbur.core.result

/** Outcome of an API call: never throws across a repository boundary. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiException) : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): ApiException? = (this as? Failure)?.error
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

inline fun <T> ApiResult<T>.onFailure(action: (ApiException) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

/** Wrap a suspending block, converting any [ApiException] or unexpected throwable into [ApiResult.Failure]. */
inline fun <T> apiCatching(block: () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: ApiException) {
        ApiResult.Failure(e)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiResult.Failure(ApiException.network(e))
    }
