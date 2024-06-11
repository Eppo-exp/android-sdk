package cloud.eppo.android;

public interface InitializationCallback {
  void onCompleted();

  void onError(String errorMessage);
}
