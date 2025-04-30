package cloud.eppo.android.helpers;

import cloud.eppo.IEppoHttpClient;

public class TestUtils {

  public static class MockHttpClient extends DelayedHttpClient {
    public MockHttpClient(byte[] responseBody) {
      super(responseBody);
      flush();
    }

    public void changeResponse(byte[] responseBody) {
      this.responseBody = responseBody;
    }
  }

  public static class ThrowingHttpClient implements IEppoHttpClient {

    @Override
    public byte[] get(String path) {
      throw new RuntimeException("Intentional Error");
    }

    @Override
    public void getAsync(String path, Callback callback) {
      callback.onFailure(new RuntimeException("Intentional Error"));
    }
  }

  public static class DelayedHttpClient implements IEppoHttpClient {
    protected byte[] responseBody;
    private Callback callback;
    private boolean flushed = false;
    private Throwable error = null;

    public DelayedHttpClient(byte[] responseBody) {
      this.responseBody = responseBody;
    }

    @Override
    public byte[] get(String path) {
      return responseBody;
    }

    @Override
    public void getAsync(String path, Callback callback) {
      if (flushed) {
        callback.onSuccess(responseBody);
      } else if (error != null) {
        callback.onFailure(error);
      } else {
        this.callback = callback;
      }
    }

    public void fail(Throwable error) {
      this.error = error;
      if (this.callback != null) {
        this.callback.onFailure(error);
      }
    }

    public void flush() {
      flushed = true;
      if (callback != null) {
        callback.onSuccess(responseBody);
      }
    }
  }
}
