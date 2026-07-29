package com.enumdev.enumdevelopment.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TabCompleteUtil {

    private TabCompleteUtil() {
    }

    public static List<String> filter(List<String> variants, String prefix) {
        if (variants == null || variants.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String variant : variants) {
            if (variant.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)) {
                result.add(variant);
            }
        }
        Collections.sort(result);
        return result;
    }
}
