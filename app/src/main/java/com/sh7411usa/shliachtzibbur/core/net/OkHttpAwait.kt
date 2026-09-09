package com.sh7411usa.shliachtzibbur.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/** Await an OkHttp [Call] without pulling in okhttp-coroutines. Cancels the call if the coroutine is cancelled. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.closeQuietly() }
        }
    })
    cont.invokeOnCancellation {
        runCatching { cancel() }
    }
}

private fun Response.closeQuietly() {
    runCatching { close() }
}
