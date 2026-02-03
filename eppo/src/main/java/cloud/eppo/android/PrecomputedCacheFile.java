package cloud.eppo.android;

import android.app.Application;

/** Disk cache file for precomputed configuration. */
public class PrecomputedCacheFile extends BaseCacheFile {

  public PrecomputedCacheFile(Application application, String fileNameSuffix) {
    super(application, cacheFileName(fileNameSuffix));
  }

  public static String cacheFileName(String suffix) {
    return "eppo-sdk-precomputed-" + suffix + ".json";
  }
}
