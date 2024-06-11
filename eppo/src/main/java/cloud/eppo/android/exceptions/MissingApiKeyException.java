package cloud.eppo.android.exceptions;

public class MissingApiKeyException extends RuntimeException {
  public MissingApiKeyException() {
    super("Missing apiKey");
  }
}
