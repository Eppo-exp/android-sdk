package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.base64Decode;
import static cloud.eppo.android.util.Utils.base64Encode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cloud.eppo.android.util.Utils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class) // Needed for anything that relies on Base64
public class UtilsTest {

  @Test
  public void testGetISODate() {
    String isoDate = Utils.getISODate(new Date());
    assertNotNull("ISO date should not be null", isoDate);

    // Verify the format
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      Date date = dateFormat.parse(isoDate);
      assertNotNull("Parsed date should not be null", date);

      // Optionally, verify the date is not too far from the current time
      long currentTime = System.currentTimeMillis();
      long parsedTime = date.getTime();
      assertTrue(
          "The parsed date should be within a reasonable range of the current time",
          Math.abs(currentTime - parsedTime) < 10000); // for example, within 10 seconds
    } catch (ParseException e) {
      fail("Parsing the ISO date failed: " + e.getMessage());
    }
  }

  @Test
  public void testGetCurrentDateISOInDifferentLocale() {
    // Arrange
    Locale defaultLocale = Locale.getDefault();
    try {
      // Set locale to Arabic
      Locale.setDefault(new Locale("ar"));
      String isoDate = Utils.getISODate(new Date());

      // Act
      // Check if the date is in the correct ISO 8601 format
      // This is a simple regex check to see if the string follows the
      // YYYY-MM-DDTHH:MM:SSZ pattern
      boolean isISO8601 = isoDate.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

      // Assert
      assertTrue("Date should be in ISO 8601 format", isISO8601);

    } catch (Exception e) {
      fail("Test failed with exception: " + e.getMessage());
    } finally {
      // Reset locale back to original
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  public void testBase64EncodeAndDecode() {
    String testInput = "a";
    String encodedInput = base64Encode(testInput);
    assertEquals("YQ==", encodedInput);
    String decodedOutput = base64Decode(encodedInput);
    assertEquals("a", decodedOutput);
  }

  @Test
  public void testGetEnvironmentFromSdkKey() {
    // SDK key with encoded params: eh=5qhpgd.e.eppo.cloud&cs=5qhpgd
    String sdkKey = "nx3VNR-S6H2RQexXkQffFKaxwd9SAr4u.ZWg9NXFocGdkLmUuZXBwby5jbG91ZCZjcz01cWhwZ2Q";
    String env = Utils.getEnvironmentFromSdkKey(sdkKey);
    assertEquals("5qhpgd", env);
  }

  @Test
  public void testGetEnvironmentFromSdkKeyWithDifferentFormat() {
    // Test another valid key format
    String sdkKey = "someRandomKey.ZWg9YWJjMTIzLmUuZXBwby5jbG91ZCZjcz1hYmMxMjM=";
    String env = Utils.getEnvironmentFromSdkKey(sdkKey);
    assertEquals("abc123", env);
  }

  @Test
  public void testGetEnvironmentFromSdkKeyReturnsNullForInvalidKey() {
    // No dot separator
    assertNull(Utils.getEnvironmentFromSdkKey("invalidKeyWithoutDot"));

    // Null key
    assertNull(Utils.getEnvironmentFromSdkKey(null));

    // Empty string
    assertNull(Utils.getEnvironmentFromSdkKey(""));

    // Invalid base64
    assertNull(Utils.getEnvironmentFromSdkKey("key.!!!invalidbase64!!!"));
  }

  @Test
  public void testGetEnvironmentFromSdkKeyWithMissingEhParam() {
    // Encoded params without "eh" parameter: cs=something
    String sdkKey = "key.Y3M9c29tZXRoaW5n";
    assertNull(Utils.getEnvironmentFromSdkKey(sdkKey));
  }
}
