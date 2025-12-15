package cloud.eppo.kotlin.internal.util

import java.security.MessageDigest

/**
 * Compute MD5 hash of input string with salt.
 *
 * @param input The string to hash
 * @param salt Salt to append to input before hashing
 * @return Hex-encoded MD5 hash
 */
internal fun getMD5Hash(input: String, salt: String?): String {
    val md5 = MessageDigest.getInstance("MD5")
    val combined = input + (salt ?: "")
    val digest = md5.digest(combined.toByteArray(Charsets.UTF_8))
    return digest.toHex()
}

/**
 * Convert byte array to hex string.
 *
 * @return Hex-encoded string
 */
private fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}
