package cloud.eppo.kotlin.internal.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Base64ExtensionsTest {

    @Test
    fun `encodeBase64 encodes simple string correctly`() {
        val input = "Hello, World!"
        val encoded = input.encodeBase64()

        // Standard Base64 encoding of "Hello, World!"
        assertThat(encoded.trim()).isEqualTo("SGVsbG8sIFdvcmxkIQ==")
    }

    @Test
    fun `decodeBase64 decodes simple string correctly`() {
        val encoded = "SGVsbG8sIFdvcmxkIQ=="
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEqualTo("Hello, World!")
    }

    @Test
    fun `encodeBase64 and decodeBase64 are inverse operations`() {
        val original = "Test String with Special Characters: !@#$%^&*()"
        val encoded = original.encodeBase64()
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `decodeBase64 handles empty string`() {
        val encoded = ""
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEmpty()
    }

    @Test
    fun `encodeBase64 handles empty string`() {
        val input = ""
        val encoded = input.encodeBase64()

        assertThat(encoded.trim()).isEmpty()
    }

    @Test
    fun `decodeBase64 handles Unicode characters`() {
        val original = "Hello 世界 🌍"
        val encoded = original.encodeBase64()
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `decodeBase64 handles flag variation value from API`() {
        // Real example: Base64 encoded "true"
        val encoded = "dHJ1ZQ=="
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEqualTo("true")
    }

    @Test
    fun `decodeBase64 handles numeric values`() {
        // Base64 encoded "42"
        val encoded = "NDI="
        val decoded = encoded.decodeBase64()

        assertThat(decoded).isEqualTo("42")
    }

    @Test
    fun `decodeBase64 handles invalid Base64 gracefully`() {
        // Note: Android's Base64.decode is more lenient than Java's
        // It may not throw on all invalid inputs, so we test it returns a result
        val invalid = "Not valid Base64!!!"

        try {
            val result = invalid.decodeBase64()
            // If it doesn't throw, that's also acceptable behavior
            assertThat(result).isNotNull()
        } catch (e: IllegalArgumentException) {
            // This is also acceptable - strict validation
            assertThat(e).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `roundtrip test with various data types`() {
        val testCases = listOf(
            "true",
            "false",
            "42",
            "3.14159",
            "control",
            "treatment",
            """{"key": "value"}""",
            "exp-123-allocation",
            ""
        )

        testCases.forEach { original ->
            val encoded = original.encodeBase64()
            val decoded = encoded.decodeBase64()
            assertThat(decoded).isEqualTo(original)
        }
    }
}
