package cloud.eppo.android.util;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Utils {
  private static final int BUFFER_SIZE = 8192;
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

  /**
   * Extracts the environment prefix from an SDK key. The SDK key format is:
   * {random_key}.{base64_encoded_params} where params decode to "eh={prefix}.e.eppo.cloud&cs=..."
   *
   * @param sdkKey The SDK key
   * @return The environment prefix (e.g., "5qhpgd"), or null if not found
   */
  public static String getEnvironmentFromSdkKey(String sdkKey) {
    if (sdkKey == null || !sdkKey.contains(".")) {
      return null;
    }

    try {
      String[] parts = sdkKey.split("\\.", 2);
      if (parts.length < 2) {
        return null;
      }

      String decoded = base64Decode(parts[1]);
      if (decoded == null) {
        return null;
      }

      // Parse query string format: "eh=5qhpgd.e.eppo.cloud&cs=5qhpgd"
      for (String param : decoded.split("&")) {
        if (param.startsWith("eh=")) {
          String envHost = param.substring(3); // Remove "eh="
          // Extract subdomain (everything before first ".")
          int dotIndex = envHost.indexOf('.');
          if (dotIndex > 0) {
            return envHost.substring(0, dotIndex);
          }
        }
      }
    } catch (Exception e) {
      // Return null if parsing fails
    }

    return null;
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

  public static byte[] toByteArray(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[BUFFER_SIZE];
    int n;
    while ((n = input.read(buffer)) != -1) {
      output.write(buffer, 0, n);
    }
    return output.toByteArray();
  }

  private static SimpleDateFormat buildUtcIsoDateFormat() {
    // Note: we don't use DateTimeFormatter.ISO_DATE so that this supports older Android versions
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    return dateFormat;
  }
}
