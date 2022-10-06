package cloud.eppo.exceptions;

public class NotInitializedException extends RuntimeException {
    public NotInitializedException() {
        super("Eppo client is not initialized");
    }
}