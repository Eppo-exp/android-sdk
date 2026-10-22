package cloud.eppo.android.framework.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 4; i++) {
        hex.append(String.format("%02x", hash[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }
}
