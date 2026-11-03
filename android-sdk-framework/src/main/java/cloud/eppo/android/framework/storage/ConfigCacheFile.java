package cloud.eppo.android.framework.storage;

import android.app.Application;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Disk cache file for flag configuration (used by FileBackedConfigStore). */
public final class ConfigCacheFile extends BaseCacheFile {
  private static final Map<String, String> CONTENT_TYPE_TO_EXTENSION = new HashMap<>();
  private static final String DEFAULT_EXTENSION = "bin";

  static {
    CONTENT_TYPE_TO_EXTENSION.put("application/json", "json");
    CONTENT_TYPE_TO_EXTENSION.put("application/x-java-serialized-object", "ser");
    CONTENT_TYPE_TO_EXTENSION.put("text/plain", "txt");
    CONTENT_TYPE_TO_EXTENSION.put("text/xml", "xml");
    CONTENT_TYPE_TO_EXTENSION.put("application/xml", "xml");
    CONTENT_TYPE_TO_EXTENSION.put("application/octet-stream", "bin");
  }

  /**
   * Creates a cache file with filename "eppo-sdk-flags-{suffix}.{ext}". Extension is derived from
   * contentType.
   */
  public ConfigCacheFile(
      @NotNull Application application, @NotNull String suffix, @NotNull String contentType) {
    super(
        application,
        "eppo-sdk-flags-"
            + suffix
            + "."
            + CONTENT_TYPE_TO_EXTENSION.getOrDefault(contentType, DEFAULT_EXTENSION));
  }
}
