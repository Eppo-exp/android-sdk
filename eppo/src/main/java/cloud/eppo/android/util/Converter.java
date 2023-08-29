package cloud.eppo.android.util;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Converter {
    public static List<Double> convertToDecimal(List<String> input) {
        List<Double> output = new LinkedList<>();
        for (String value: input) {
            output.add( Double.parseDouble(value));
        }
        return output;
    }

    public static List<Boolean> convertToBoolean(List<String> input) {
        List<Boolean> output = new LinkedList<>();
        for (String value: input) {
            output.add( Boolean.parseBoolean(value));
        }
        return output;
    }
}
