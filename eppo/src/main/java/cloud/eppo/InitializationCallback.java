package cloud.eppo;

public interface InitializationCallback {
    void onCompleted();

    void onError(String errorMessage);
}
