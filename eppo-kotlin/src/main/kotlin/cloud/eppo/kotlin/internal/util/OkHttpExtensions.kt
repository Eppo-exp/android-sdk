package cloud.eppo.kotlin.internal.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspend extension for OkHttp Call to integrate with coroutines.
 *
 * This extension properly handles:
 * - Coroutine cancellation (cancels the HTTP call)
 * - Network errors
 * - Response delivery
 *
 * Note: resume() and resumeWithException() are safe to call on cancelled
 * continuations - they are idempotent and will be ignored if already resumed.
 *
 * @return The HTTP response
 * @throws IOException for network errors
 */
internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            this.cancel()
        }

        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }
}
