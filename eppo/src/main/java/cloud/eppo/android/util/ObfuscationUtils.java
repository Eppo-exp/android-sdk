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
   * Generates the first N hex characters of an MD5 hash. More efficient than md5Hex().substring()
   * when only a prefix is needed, as it avoids converting unused bytes.
   *
   * @param input The string to hash
   * @param salt Optional salt to prepend to the input (can be null)
   * @param hexLength Number of hex characters to return (max 32, must be even)
   * @return First hexLength characters of the MD5 hex hash
   */
  public static String md5HexPrefix(String input, String salt, int hexLength) {
    if (hexLength <= 0 || hexLength > 32) {
      throw new IllegalArgumentException("hexLength must be between 1 and 32");
    }
    String saltedInput = salt != null ? salt + input : input;
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(saltedInput.getBytes(StandardCharsets.UTF_8));
      // Only convert the bytes we need (2 hex chars per byte)
      int bytesNeeded = (hexLength + 1) / 2;
      return bytesToHex(digest, bytesNeeded).substring(0, hexLength);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("MD5 algorithm not available", e);
    }
  }

  /**
   * Converts a byte array to a hexadecimal string using a pre-computed lookup table. This avoids
   * creating intermediate String objects for each byte (as Integer.toHexString would).
   */
  private static String bytesToHex(byte[] bytes) {
    return bytesToHex(bytes, bytes.length);
  }

  /**
   * Converts the first N bytes of an array to a hexadecimal string. Unrolled loop for the common
   * case of 4 bytes (8 hex chars) to help the compiler optimize. See iOS SDK PR #93.
   */
  private static String bytesToHex(byte[] bytes, int byteCount) {
    // Fast path: 4 bytes (8 hex chars) - unrolled for compiler optimization
    if (byteCount == 4) {
      return new String(
          new char[] {
            HEX_DIGITS[(bytes[0] & 0xFF) >>> 4],
            HEX_DIGITS[bytes[0] & 0x0F],
            HEX_DIGITS[(bytes[1] & 0xFF) >>> 4],
            HEX_DIGITS[bytes[1] & 0x0F],
            HEX_DIGITS[(bytes[2] & 0xFF) >>> 4],
            HEX_DIGITS[bytes[2] & 0x0F],
            HEX_DIGITS[(bytes[3] & 0xFF) >>> 4],
            HEX_DIGITS[bytes[3] & 0x0F]
          });
    }

    // General case
    char[] hexChars = new char[byteCount * 2];
    for (int i = 0; i < byteCount; i++) {
      int v = bytes[i] & 0xFF;
      hexChars[i * 2] = HEX_DIGITS[v >>> 4];
      hexChars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
    }
    return new String(hexChars);
  }
}
