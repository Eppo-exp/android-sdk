package cloud.eppo.kotlin.internal.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Md5Test {

    @Test
    fun `getMD5Hash produces consistent hash`() {
        val input = "test-flag-key"
        val salt = "abc123"

        val hash1 = getMD5Hash(input, salt)
        val hash2 = getMD5Hash(input, salt)

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `getMD5Hash produces different hash with different salt`() {
        val input = "test-flag-key"
        val salt1 = "salt1"
        val salt2 = "salt2"

        val hash1 = getMD5Hash(input, salt1)
        val hash2 = getMD5Hash(input, salt2)

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `getMD5Hash produces different hash with different input`() {
        val input1 = "flag-key-1"
        val input2 = "flag-key-2"
        val salt = "abc123"

        val hash1 = getMD5Hash(input1, salt)
        val hash2 = getMD5Hash(input2, salt)

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `getMD5Hash returns 32 character hex string`() {
        val input = "test"
        val salt = "salt"

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
    }

    @Test
    fun `getMD5Hash handles null salt`() {
        val input = "test-flag-key"
        val hash = getMD5Hash(input, null)

        // Should hash just the input without salt
        assertThat(hash).hasLength(32)
        assertThat(hash).isNotEmpty()
    }

    @Test
    fun `getMD5Hash handles empty string`() {
        val input = ""
        val salt = "salt"

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
        // Should be deterministic
        assertThat(getMD5Hash(input, salt)).isEqualTo(hash)
    }

    @Test
    fun `getMD5Hash handles empty salt`() {
        val input = "test"
        val salt = ""

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
        // Verify it's the MD5 of just "test" (098f6bcd4621d373cade4e832627b4f6)
        assertThat(hash).isEqualTo("098f6bcd4621d373cade4e832627b4f6")
    }

    @Test
    fun `getMD5Hash is deterministic for known input`() {
        val input = "feature-flag"
        val salt = "eppo-salt"

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
        // Verify determinism
        assertThat(getMD5Hash(input, salt)).isEqualTo(hash)
    }

    @Test
    fun `getMD5Hash handles special characters`() {
        val input = "flag-key-with-special!@#$%^&*()"
        val salt = "salt123"

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
    }

    @Test
    fun `getMD5Hash handles Unicode characters`() {
        val input = "feature-世界"
        val salt = "salt"

        val hash = getMD5Hash(input, salt)

        assertThat(hash).hasLength(32)
        assertThat(hash).matches("[0-9a-f]{32}")
    }

    @Test
    fun `getMD5Hash is deterministic for flag key obfuscation`() {
        // Simulate real usage: obfuscating flag keys
        val flagKey = "show-new-checkout-flow"
        val serverSalt = "abc123xyz"

        val hash1 = getMD5Hash(flagKey, serverSalt)
        val hash2 = getMD5Hash(flagKey, serverSalt)
        val hash3 = getMD5Hash(flagKey, serverSalt)

        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash2).isEqualTo(hash3)
    }
}
