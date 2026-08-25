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

  /**
   * Creates a cache file with filename "eppo-sdk-flags-{configType}-{suffix}.{ext}". Used when the
   * logical suffix is split into config type and suffix (e.g. for FileBackedConfigStore).
   *
   * @deprecated Use {@link #ConfigCacheFile(Application, String, String)} instead.
   */
  ConfigCacheFile(
      @NotNull Application application,
      @NotNull String configType,
      @NotNull String suffix,
      @NotNull String contentType) {
    this(application, configType + "-" + suffix, contentType);
  }

  /**
   * Creates a cache file with the given full file name (no prefix). Used when the caller supplies
   * the complete filename (e.g. baseName + "." + extension).
   *
   * @deprecated Use {@link #ConfigCacheFile(Application, String, String)} instead.
   */
  ConfigCacheFile(@NotNull Application application, @NotNull String fullFileName) {
    super(application, fullFileName);
  }
}
