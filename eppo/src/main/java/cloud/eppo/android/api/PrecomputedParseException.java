package cloud.eppo.android.api;

/**
 * Exception thrown when parsing precomputed configuration fails.
 *
 * <p>This exception is thrown by {@link PrecomputedConfigParser} implementations when JSON parsing
 * encounters an error.
 */
public class PrecomputedParseException extends Exception {

  public PrecomputedParseException(String message) {
    super(message);
  }

  public PrecomputedParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
