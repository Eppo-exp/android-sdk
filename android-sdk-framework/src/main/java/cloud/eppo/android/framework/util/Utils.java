package cloud.eppo.android.framework.util;

public class Utils {
  public static String logTag(Class<?> loggingClass) {
    if (loggingClass == null) {
      return "EppoSDK";
    }
    // Common prefix can make filtering logs easier
    String logTag = ("EppoSDK:" + loggingClass.getSimpleName());

    // Android prefers keeping log tags 23 characters or less
    if (logTag.length() > 23) {
      logTag = logTag.substring(0, 23);
    }

    return logTag;
  }

  public static String safeCacheKey(String key) {
    if (key == null || key.isEmpty()) {
      return "";
    }
    // Take the first eight characters to avoid the key being sensitive information.
    // Remove non-alphanumeric characters so it plays nice with filesystem paths.
    // \W is equivalent to [^a-zA-Z0-9_] — underscores are kept, which is fine for filenames.
    // Note: if the first 8 characters are all non-word the result is an empty string,
    // which produces the filename "eppo-sdk-flags-.bin". Eppo-issued API keys always start
    // with alphanumeric characters, so this edge case is not expected in production.
    return key.substring(0, Math.min(8, key.length())).replaceAll("\\W", "");
  }
}
