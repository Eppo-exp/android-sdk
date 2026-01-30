package cloud.eppo.android;

import android.app.Application;

/** Disk cache file for standard flag configuration. */
public class ConfigCacheFile extends BaseCacheFile {

  public ConfigCacheFile(Application application, String fileNameSuffix) {
    super(application, cacheFileName(fileNameSuffix));
  }

  public static String cacheFileName(String suffix) {
    return "eppo-sdk-config-v4-flags-" + suffix + ".json";
  }
}
