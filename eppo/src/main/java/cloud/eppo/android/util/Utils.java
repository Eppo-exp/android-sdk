package cloud.eppo.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {
  private static final SimpleDateFormat isoUtcDateFormat = buildUtcIsoDateFormat();

  public static String base64Encode(String input) {
    if (input == null) {
      return null;
    }
    String result = Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    if (result == null) {
      throw new RuntimeException(
          "null output from Base64; if not running on Android hardware be sure to use RobolectricTestRunner");
    }
    return result;
  }

  public static String base64Decode(String input) {
    if (input == null) {
      return null;
    }
    byte[] decodedBytes = Base64.decode(input, Base64.NO_WRAP);
    if (decodedBytes.length == 0 && !input.isEmpty()) {
      throw new RuntimeException(
          "zero byte output from Base64; if not running on Android hardware be sure to use RobolectricTestRunner");
    }
    return new String(decodedBytes);
  }

  public static void validateNotEmptyOrNull(String input, String errorMessage) {
    if (input == null || input.isEmpty()) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  public static String getISODate(Date date) {
    return isoUtcDateFormat.format(date);
  }

  public static String safeCacheKey(String key) {
    // Take the first eight characters to avoid the key being sensitive information
    // Remove non-alphanumeric characters so it plays nice with filesystem
    return key.substring(0, 8).replaceAll("\\W", "");
  }

  public static SharedPreferences getSharedPrefs(Context context, String keySuffix) {
    return context.getSharedPreferences("eppo-" + keySuffix, Context.MODE_PRIVATE);
  }

  public static String logTag(Class loggingClass) {
    // Common prefix can make filtering logs easier
    String logTag = ("EppoSDK:" + loggingClass.getSimpleName());

    // Android prefers keeping log tags 23 characters or less
    if (logTag.length() > 23) {
      logTag = logTag.substring(0, 23);
    }

    return logTag;
  }

  private static SimpleDateFormat buildUtcIsoDateFormat() {
    // Note: we don't use DateTimeFormatter.ISO_DATE so that this supports older Android versions
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    return dateFormat;
  }
}
