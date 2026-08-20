package cloud.eppo.android.framework.util;

public class Utils {
  public static String logTag(Class loggingClass) {
    // Common prefix can make filtering logs easier
    String logTag = ("EppoSDK:" + loggingClass.getSimpleName());

    // Android prefers keeping log tags 23 characters or less
    if (logTag.length() > 23) {
      logTag = logTag.substring(0, 23);
    }

    return logTag;
  }

  public static String safeCacheKey(String key) {
    // Take the first eight characters to avoid the key being sensitive information
    // Remove non-alphanumeric characters so it plays nice with filesystem
    return key.substring(0, 8).replaceAll("\\W", "");
  }
}
