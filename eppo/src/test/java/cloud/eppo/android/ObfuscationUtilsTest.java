package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import cloud.eppo.android.util.ObfuscationUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ObfuscationUtilsTest {

  @Test
  public void testMd5HexWithoutSalt() {
    // Standard MD5 test vectors
    String result = ObfuscationUtils.md5Hex("");
    assertEquals("d41d8cd98f00b204e9800998ecf8427e", result);

    result = ObfuscationUtils.md5Hex("a");
    assertEquals("0cc175b9c0f1b6a831c399e269772661", result);

    result = ObfuscationUtils.md5Hex("abc");
    assertEquals("900150983cd24fb0d6963f7d28e17f72", result);

    result = ObfuscationUtils.md5Hex("message digest");
    assertEquals("f96b697d7cb7938d525a2f31aaf161d0", result);
  }

  @Test
  public void testMd5HexWithSalt() {
    // With salt, the input becomes salt + input
    String result = ObfuscationUtils.md5Hex("flag_key", "my-salt");
    assertNotNull(result);
    assertEquals(32, result.length()); // MD5 always produces 32 hex chars

    // Verify that salt is prepended
    String withoutSalt = ObfuscationUtils.md5Hex("flag_key");
    String saltAndInput = ObfuscationUtils.md5Hex("my-saltflag_key");
    String withSalt = ObfuscationUtils.md5Hex("flag_key", "my-salt");

    // The result with salt should equal md5(salt + input)
    assertEquals(saltAndInput, withSalt);
    // And should not equal md5(input) alone
    assertNotNull(withoutSalt);
  }

  @Test
  public void testMd5HexWithNullSalt() {
    // Null salt should behave like no salt
    String withNullSalt = ObfuscationUtils.md5Hex("test", null);
    String withoutSalt = ObfuscationUtils.md5Hex("test");
    assertEquals(withoutSalt, withNullSalt);
  }

  @Test
  public void testMd5HexConsistency() {
    // Same input should always produce same output
    String input = "consistent-input";
    String salt = "consistent-salt";

    String result1 = ObfuscationUtils.md5Hex(input, salt);
    String result2 = ObfuscationUtils.md5Hex(input, salt);
    assertEquals(result1, result2);
  }

  @Test
  public void testMd5HexLowercase() {
    // Result should always be lowercase
    String result = ObfuscationUtils.md5Hex("ABC");
    assertEquals(result.toLowerCase(), result);
  }

  @Test
  public void testMd5HexLength() {
    // MD5 should always produce 32 character hex string
    String[] inputs = {
      "", "a", "test", "a longer string with spaces", "unicode: \u00e9\u00e8\u00ea"
    };

    for (String input : inputs) {
      String result = ObfuscationUtils.md5Hex(input);
      assertEquals("MD5 hash should be 32 characters for input: " + input, 32, result.length());
    }
  }

  // md5HexPrefix tests

  @Test
  public void testMd5HexPrefixMatchesFullHash() {
    // md5HexPrefix should return the same prefix as md5Hex().substring()
    String input = "test-input";
    String salt = "test-salt";

    String fullHash = ObfuscationUtils.md5Hex(input, salt);

    // Test various prefix lengths
    for (int len = 1; len <= 32; len++) {
      String prefix = ObfuscationUtils.md5HexPrefix(input, salt, len);
      assertEquals(len, prefix.length());
      assertEquals(fullHash.substring(0, len), prefix);
    }
  }

  @Test
  public void testMd5HexPrefix8Chars() {
    // Common case: 8 character prefix (used for cache file naming)
    String prefix = ObfuscationUtils.md5HexPrefix("subject-key", null, 8);
    assertEquals(8, prefix.length());

    // Should match substring of full hash
    String fullHash = ObfuscationUtils.md5Hex("subject-key");
    assertEquals(fullHash.substring(0, 8), prefix);
  }

  @Test
  public void testMd5HexPrefixWithSalt() {
    String prefix = ObfuscationUtils.md5HexPrefix("input", "salt", 8);
    String fullWithSalt = ObfuscationUtils.md5Hex("input", "salt");
    assertEquals(fullWithSalt.substring(0, 8), prefix);
  }

  @Test
  public void testMd5HexPrefixInvalidLength() {
    assertThrows(
        IllegalArgumentException.class, () -> ObfuscationUtils.md5HexPrefix("test", null, 0));

    assertThrows(
        IllegalArgumentException.class, () -> ObfuscationUtils.md5HexPrefix("test", null, -1));

    assertThrows(
        IllegalArgumentException.class, () -> ObfuscationUtils.md5HexPrefix("test", null, 33));
  }

  @Test
  public void testMd5HexPrefixConsistency() {
    // Same input should always produce same output
    String input = "consistent";
    String prefix1 = ObfuscationUtils.md5HexPrefix(input, null, 8);
    String prefix2 = ObfuscationUtils.md5HexPrefix(input, null, 8);
    assertEquals(prefix1, prefix2);
  }
}
