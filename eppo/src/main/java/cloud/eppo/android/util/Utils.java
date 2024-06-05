package cloud.eppo.android.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonElement;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.adapters.FlagConfigResponseAdapter;

public class Utils {

    private static final String TAG = logTag(FlagConfigResponseAdapter.class);

    private static final SimpleDateFormat isoUtcDateFormat = buildUtcIsoDateFormat();

    public static String getMD5Hex(String input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error computing md5 hash", e);
        }
        byte[] messageDigest = md.digest(input.getBytes());
        BigInteger no = new BigInteger(1, messageDigest);
        String hashText = no.toString(16);
        while (hashText.length() < 32) {
            hashText = "0" + hashText;
        }

        return hashText;
    }

    public static String base64Encode(String input) {
        if (input == null) {
            return null;
        }
        String result = Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        if (result == null) {
            throw new RuntimeException("null output from Base64; if not running on Android hardware be sure to use RobolectricTestRunner");
        }
        return result;
    }

    public static String base64Decode(String input) {
        if (input == null) {
            return null;
        }
        byte[] decodedBytes = Base64.decode(input, Base64.NO_WRAP);
        if (decodedBytes.length == 0 && !input.isEmpty()) {
            throw new RuntimeException("zero byte output from Base64; if not running on Android hardware be sure to use RobolectricTestRunner");
        }
        return new String(decodedBytes);
    }

    public static int getShard(String input, int maxShardValue) {
        String hashText = getMD5Hex(input);
        while (hashText.length() < 32) {
            hashText = "0" + hashText;
        }
        return (int) (Long.parseLong(hashText.substring(0, 8), 16) % maxShardValue);
    }

    public static boolean isShardInRange(int shard, Range range) {
        return shard >= range.getStart() && shard < range.getEnd();
    }

    public static void validateNotEmptyOrNull(String input, String errorMessage) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static Date parseUtcISODateElement(JsonElement isoDateStringElement) {
        if (isoDateStringElement == null || isoDateStringElement.isJsonNull()) {
            return null;
        }
        String isoDateString = isoDateStringElement.getAsString();
        Date result = null;
        try {
            result = isoUtcDateFormat.parse(isoDateString);
        } catch (ParseException e) {
            // We expect to fail parsing if the date is base 64 encoded
            // Thus we'll leave the result null for now and try again with the decoded value
        }

        if (result == null) {
            // Date may be encoded
            String decodedIsoDateString = base64Decode(isoDateString);
            try {
                result = isoUtcDateFormat.parse(decodedIsoDateString);
            } catch (ParseException e) {
                Log.w(TAG, "Date \""+isoDateString+"\" not in ISO date format");
            }
        }

        return result;
    }

    public static String getISODate(Date date) {
        return isoUtcDateFormat.format(date);
    }

    public static String safeCacheKey(String key) {
        // Take the first eight characters to avoid the key being sensitive information
        // Remove non-alphanumeric characters so it plays nice with filesystem
        return key.substring(0,8).replaceAll("\\W", "");
    }

    public static String generateExperimentKey(String featureFlagKey, String allocationKey) {
        return featureFlagKey + '-' + allocationKey;
    }

    public static SharedPreferences getSharedPrefs(Context context, String keySuffix) {
        return context.getSharedPreferences("eppo-"+keySuffix, Context.MODE_PRIVATE);
    }

    public static String logTag(Class loggingClass) {
        // Common prefix can make filtering logs easier
        String logTag = ("EppoSDK:"+loggingClass.getSimpleName());

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
