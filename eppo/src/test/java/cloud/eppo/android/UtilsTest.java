package cloud.eppo.android;

import org.junit.Test;
import static org.junit.Assert.*;

import static cloud.eppo.android.util.Utils.parseUtcISODateElement;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import cloud.eppo.android.util.Utils;

public class UtilsTest {

    @Test
    public void testParseUtcISODateElement() {
        JsonElement object = JsonParser.parseString("\"2024-05-01T16:13:26.651Z\"");
        Date parsedDate = parseUtcISODateElement(object);
        Date expectedDate = new Date(1714580006651L);
        assertEquals(expectedDate, parsedDate);

        object = JsonParser.parseString("null");
        parsedDate = parseUtcISODateElement(object);
        assertNull(parsedDate);

        parsedDate = parseUtcISODateElement(null);
        assertNull(parsedDate);
    }

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
            assertTrue("The parsed date should be within a reasonable range of the current time",
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
}