package cloud.eppo.kotlin

/**
 * Base exception for Eppo SDK errors.
 *
 * These exceptions are only thrown when gracefulMode is disabled.
 * When gracefulMode is enabled, errors are logged and default values are returned.
 */
open class EppoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when a flag is not found in precomputed values.
 *
 * @param flagKey The flag key that was not found
 */
class FlagNotFoundException(flagKey: String) : EppoException("Flag not found: $flagKey")

/**
 * Thrown when flag type doesn't match expected type.
 *
 * @param flagKey The flag key
 * @param expected The expected type
 * @param actual The actual type
 */
class TypeMismatchException(
    flagKey: String,
    expected: String,
    actual: String
) : EppoException("Type mismatch for flag '$flagKey': expected $expected but got $actual")

/**
 * Thrown when flag value cannot be parsed.
 *
 * @param flagKey The flag key
 * @param cause The underlying parse error
 */
class ParseException(
    flagKey: String,
    cause: Throwable
) : EppoException("Failed to parse flag value: $flagKey", cause)

/**
 * Thrown when client is not initialized.
 */
class NotInitializedException : EppoException("Client not initialized. Call fetchPrecomputedFlags() first.")

/**
 * Thrown when a required parameter is missing or invalid.
 *
 * @param paramName The parameter name
 */
class InvalidParameterException(
    paramName: String
) : EppoException("Invalid parameter: $paramName")
