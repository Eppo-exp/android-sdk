package cloud.eppo.android.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Utility class for obfuscation operations used in precomputed flag lookups. */
public final class ObfuscationUtils {

  /** Pre-computed hex character lookup table for efficient byte-to-hex conversion. */
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private ObfuscationUtils() {
    // Prevent instantiation
  }

  /**
   * Generates an MD5 hash of the input string with an optional salt.
   *
   * @param input The string to hash
   * @param salt Optional salt to prepend to the input (can be null)
   * @return 32-character lowercase hexadecimal MD5 hash
   */
  public static String md5Hex(String input, String salt) {
    String saltedInput = salt != null ? salt + input : input;
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 algorithm not available", e);
    }
  }

  /**
   * Generates an MD5 hash of the input string (without salt).
   *
   * @param input The string to hash
   * @return 32-character lowercase hexadecimal MD5 hash
   */
  public static String md5Hex(String input) {
    return md5Hex(input, null);
  }

  /**
   * Converts a byte array to a hexadecimal string using a pre-computed lookup table. This avoids
   * creating intermediate String objects for each byte (as Integer.toHexString would).
   */
  private static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      hexChars[i * 2] = HEX_DIGITS[v >>> 4];
      hexChars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
    }
    return new String(hexChars);
  }
}
