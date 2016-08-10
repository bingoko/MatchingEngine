package com.lykke.matching.engine.utils;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import static java.math.BigDecimal.*;

public class RoundingUtils {
    private static DecimalFormat FORMAT = initFormat();

    private static DecimalFormat initFormat() {
        DecimalFormat df = new DecimalFormat("#.#");
        df.setMaximumFractionDigits(8);
        return df;
    }

    public static Double round(Double value, int accuracy, boolean roundUp) {
        return new BigDecimal(value).setScale(accuracy + 8, ROUND_HALF_UP).setScale(accuracy, roundUp ? ROUND_UP : ROUND_DOWN).doubleValue();
    }

    public static String roundForPrint(Double value) {
        return FORMAT.format(value);
    }
}