package cloud.eppo.kotlin.internal.util

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test
import java.io.IOException

class OkHttpExtensionsTest {

    @Test
    fun `await returns response on successful call`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val expectedResponse = createMockResponse(code = 200, message = "OK")

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onResponse(mockCall, expectedResponse)
        }

        val response = mockCall.await()

        assertThat(response).isEqualTo(expectedResponse)
        assertThat(response.code).isEqualTo(200)
    }

    @Test(expected = IOException::class)
    fun `await throws IOException on network failure`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val networkError = IOException("Network unreachable")

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(mockCall, networkError)
        }

        mockCall.await()
    }

    @Test
    fun `await cancels HTTP call when coroutine is cancelled`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        var enqueued = false

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            enqueued = true
            // Don't call callback - simulate slow network
        }

        val job = launch {
            mockCall.await()
        }

        // Wait for enqueue
        while (!enqueued) { /* wait */ }

        // Cancel the coroutine before response arrives
        job.cancel()
        job.join()

        // Verify cancel was called on the HTTP call
        verify { mockCall.cancel() }
    }

    @Test
    fun `await handles successful response with body`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val response = createMockResponse(code = 200, message = "OK")

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onResponse(mockCall, response)
        }

        val result = mockCall.await()

        assertThat(result.isSuccessful).isTrue()
        assertThat(result.code).isEqualTo(200)
    }

    @Test
    fun `await handles HTTP error responses`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val errorResponse = createMockResponse(code = 404, message = "Not Found")

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onResponse(mockCall, errorResponse)
        }

        val result = mockCall.await()

        assertThat(result.code).isEqualTo(404)
        assertThat(result.isSuccessful).isFalse()
    }

    @Test(expected = IOException::class)
    fun `await propagates timeout exception`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        val timeoutError = IOException("timeout")

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            callbackSlot.captured.onFailure(mockCall, timeoutError)
        }

        mockCall.await()
    }

    @Test
    fun `await handles response after cancellation gracefully`() = runTest {
        val mockCall = mockk<Call>(relaxed = true)
        val callbackSlot = slot<Callback>()
        var enqueued = false

        every { mockCall.enqueue(capture(callbackSlot)) } answers {
            enqueued = true
        }

        val job = launch {
            mockCall.await()
        }

        // Wait for enqueue
        while (!enqueued) { /* wait */ }

        // Cancel before callback fires
        job.cancel()
        job.join()

        // Fire callback after cancellation (simulating race condition)
        // This should be handled gracefully by the continuation
        val response = createMockResponse(code = 200, message = "OK")
        callbackSlot.captured.onResponse(mockCall, response)

        // No assertion needed - just verify no crash
        // The continuation will ignore the response since it's already cancelled
        verify { mockCall.cancel() }
    }

    // Helper function to create mock responses
    private fun createMockResponse(code: Int, message: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .build()
    }
}
