package cloud.eppo;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import cloud.eppo.android.framework.storage.ByteStore;
import cloud.eppo.android.framework.storage.CachingConfigurationStore;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.api.Configuration;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.http.EppoConfigurationRequestFactory;
import cloud.eppo.parser.ConfigurationParser;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class InitialConfigurationThreadReviewTest {
  @Test
  public void completedInitialConfigurationDoesNotSerializeOnCaller() throws Exception {
    Thread caller = Thread.currentThread();
    Configuration config = mock(Configuration.class);
    ConfigurationCodec<Configuration> codec = mock(ConfigurationCodec.class);
    when(codec.toBytes(config))
        .thenAnswer(
            invocation -> {
              assertNotSame(
                  "Completed initial configuration must not serialize inline",
                  caller,
                  Thread.currentThread());
              return new byte[] {1};
            });
    ByteStore bytes = mock(ByteStore.class);
    when(bytes.write(any())).thenReturn(CompletableFuture.completedFuture(null));
    CachingConfigurationStore<Configuration> store =
        new CachingConfigurationStore<Configuration>(codec, bytes) {};
    EppoConfigurationRequestor<Configuration, Object> requestor =
        new EppoConfigurationRequestor<>(
            store,
            false,
            mock(ConfigurationParser.class),
            mock(EppoConfigurationClient.class),
            mock(EppoConfigurationRequestFactory.class));
    assertTrue(
        requestor
            .setInitialConfiguration(CompletableFuture.completedFuture(config))
            .get(5, TimeUnit.SECONDS));
    verify(codec).toBytes(config);
    assertSame(config, store.getConfiguration());
  }
}
