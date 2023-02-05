package cloud.eppo.android.exceptions;

public class MissingApplicationException extends RuntimeException {
    public MissingApplicationException() {
        super("Missing context");
    }
}
