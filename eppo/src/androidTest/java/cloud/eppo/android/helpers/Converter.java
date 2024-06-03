package cloud.eppo.android.helpers;

import java.util.LinkedList;
import java.util.List;

public class Converter {
    public static List<Double> convertToDouble(List<String> input) {
        List<Double> output = new LinkedList<>();
        for (String value : input) {
            output.add(Double.parseDouble(value));
        }
        return output;
    }

    public static List<Boolean> convertToBoolean(List<String> input) {
        List<Boolean> output = new LinkedList<>();
        for (String value : input) {
            output.add(Boolean.parseBoolean(value));
        }
        return output;
    }
}
