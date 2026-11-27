package cloud.eppo.android;

import android.app.Application;
import cloud.eppo.android.framework.storage.BaseCacheFile;

/**
 * Test helper class for manipulating configuration cache files. This is used in tests to pre-seed
 * or inspect cache files.
 */
public class TestConfigCacheFile extends BaseCacheFile {

  public TestConfigCacheFile(Application application, String fileNameSuffix) {
    super(application, cacheFileName(fileNameSuffix));
  }

  public static String cacheFileName(String suffix) {
    // Match the naming convention used by ConfigCacheFile with Java serialization codec
    // ConfigurationCodec.Default uses content type "application/x-java-serialized-object"
    // which maps to ".ser" extension in ConfigCacheFile
    return "eppo-sdk-flags-" + suffix + ".ser";
  }
}
