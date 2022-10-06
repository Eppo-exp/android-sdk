package cloud.eppo.android.exceptions;

public class NotInitializedException extends RuntimeException {
    public NotInitializedException() {
        super("Eppo client is not initialized");
    }
}