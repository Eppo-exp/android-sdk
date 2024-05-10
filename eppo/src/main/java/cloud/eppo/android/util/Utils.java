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
import cloud.eppo.android.dto.deserializers.FlagConfigResponseDeserializer;

public class Utils {

    private static final String TAG = logTag(FlagConfigResponseDeserializer.class);

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

        return Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public static String base64Decode(String input) {
        if (input == null) {
            return null;
        }
        return new String(Base64.decode(input, Base64.NO_WRAP));
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
            Log.w(TAG, "Date \"+isoDateString+\" not in ISO date format");
        }
        return result;
    }

    public static String getISODate(Date date) {
        return isoUtcDateFormat.format(date);
    }

    public static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences("eppo", Context.MODE_PRIVATE);
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
