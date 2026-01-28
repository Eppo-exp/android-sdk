package cloud.eppo.android.exceptions;

/** Exception thrown when a subject key is required but not provided. */
public class MissingSubjectKeyException extends RuntimeException {
  public MissingSubjectKeyException() {
    super("Missing subjectKey");
  }
}
