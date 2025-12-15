package cloud.eppo.kotlin.internal.util

import android.util.Base64

/**
 * Decode a Base64 encoded string.
 *
 * Uses NO_WRAP flag to avoid newlines in the output.
 *
 * @return Decoded string
 * @throws IllegalArgumentException if the input is not valid Base64
 */
internal fun String.decodeBase64(): String {
    return try {
        Base64.decode(this, Base64.NO_WRAP).toString(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid Base64 string: $this", e)
    }
}

/**
 * Encode a string to Base64.
 *
 * Uses NO_WRAP flag to avoid newlines in the output.
 *
 * @return Base64 encoded string
 */
internal fun String.encodeBase64(): String {
    return Base64.encodeToString(this.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
