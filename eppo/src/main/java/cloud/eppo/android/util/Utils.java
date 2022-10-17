package cloud.eppo.android.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import cloud.eppo.android.dto.ShardRange;

public class Utils {
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

    public static int getShard(String input, int maxShardValue) {
        String hashText = getMD5Hex(input);
        while (hashText.length() < 32) {
            hashText = "0" + hashText;
        }
        return (int) (Long.parseLong(hashText.substring(0, 8), 16) % maxShardValue);
    }

    public static boolean isShardInRange(int shard, ShardRange range) {
        return shard >= range.getStart() && shard < range.getEnd();
    }

    public static void validateNotEmptyOrNull(String input, String errorMessage) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    public static String getISODate(Date date) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        return df.format(date);
    }
}
