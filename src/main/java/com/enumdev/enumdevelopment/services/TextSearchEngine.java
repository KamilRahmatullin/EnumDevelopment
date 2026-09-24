package com.enumdev.enumdevelopment.services;

import com.enumdev.enumdevelopment.utils.ColorCodeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Поиск и замена текста в строке с учётом флагов: регистр и цветовые коды.
 * В цветонезависимом режиме сравнение идёт по «чистому» тексту, поэтому
 * градиент любого формата не мешает найти надпись, а при замене цвета
 * переносятся на новый текст.
 */
public final class TextSearchEngine {

    private TextSearchEngine() {
    }

    public static int count(String line, String target, MatchSettings settings) {
        if (line == null || target == null || target.isEmpty()) {
            return 0;
        }
        if (!settings.isIgnoreColors()) {
            return findMatches(line, target, settings.isCaseSensitive()).size();
        }

        String needle = ColorCodeUtil.strip(target);
        if (needle.isEmpty()) {
            return 0;
        }
        ColorCodeUtil.ColorText colorText = ColorCodeUtil.analyze(line);
        return findMatches(colorText.getPlain(), needle, settings.isCaseSensitive()).size();
    }

    public static ReplaceResult replace(String line, String target, String replacement, MatchSettings settings) {
        if (line == null || target == null || target.isEmpty()) {
            return new ReplaceResult(line, 0);
        }
        String newText = replacement == null ? "" : replacement;

        if (!settings.isIgnoreColors()) {
            return replacePlain(line, target, newText, settings.isCaseSensitive());
        }
        return replaceKeepingColors(line, target, newText, settings);
    }

    private static ReplaceResult replacePlain(String line, String target, String replacement, boolean caseSensitive) {
        List<int[]> matches = findMatches(line, target, caseSensitive);
        if (matches.isEmpty()) {
            return new ReplaceResult(line, 0);
        }

        StringBuilder builder = new StringBuilder(line.length());
        int cursor = 0;
        for (int[] match : matches) {
            builder.append(line, cursor, match[0]);
            builder.append(replacement);
            cursor = match[1];
        }
        builder.append(line, cursor, line.length());
        return new ReplaceResult(builder.toString(), matches.size());
    }

    private static ReplaceResult replaceKeepingColors(String line, String target, String replacement, MatchSettings settings) {
        String needle = ColorCodeUtil.strip(target);
        if (needle.isEmpty()) {
            return new ReplaceResult(line, 0);
        }

        ColorCodeUtil.ColorText colorText = ColorCodeUtil.analyze(line);
        String plain = colorText.getPlain();
        List<int[]> matches = findMatches(plain, needle, settings.isCaseSensitive());
        if (matches.isEmpty()) {
            return new ReplaceResult(line, 0);
        }

        boolean keepColors = settings.isKeepColors()
                && !ColorCodeUtil.hasColorCodes(replacement);
        String newText = settings.isKeepColors() ? replacement : ColorCodeUtil.strip(replacement);

        StringBuilder builder = new StringBuilder(line.length());
        int matchIndex = 0;
        int plainIndex = 0;
        int plainLength = plain.length();

        while (plainIndex <= plainLength) {
            if (matchIndex < matches.size() && matches.get(matchIndex)[0] == plainIndex) {
                int[] match = matches.get(matchIndex);
                builder.append(keepColors
                        ? colorText.redistribute(match[0], match[1], newText)
                        : newText);
                plainIndex = match[1];
                matchIndex++;
                continue;
            }

            for (String marker : colorText.markersAt(plainIndex)) {
                builder.append(marker);
            }
            if (plainIndex < plainLength) {
                builder.append(plain.charAt(plainIndex));
            }
            plainIndex++;
        }

        return new ReplaceResult(builder.toString(), matches.size());
    }

    private static List<int[]> findMatches(String haystack, String needle, boolean caseSensitive) {
        List<int[]> matches = new ArrayList<int[]>();
        if (haystack.isEmpty() || needle.isEmpty() || needle.length() > haystack.length()) {
            return matches;
        }

        int index = 0;
        while (index + needle.length() <= haystack.length()) {
            if (haystack.regionMatches(!caseSensitive, index, needle, 0, needle.length())) {
                matches.add(new int[]{index, index + needle.length()});
                index += needle.length();
                continue;
            }
            index++;
        }
        return matches;
    }

    public static final class MatchSettings {
        private final boolean caseSensitive;
        private final boolean ignoreColors;
        private final boolean keepColors;

        public MatchSettings(boolean caseSensitive, boolean ignoreColors, boolean keepColors) {
            this.caseSensitive = caseSensitive;
            this.ignoreColors = ignoreColors;
            this.keepColors = keepColors;
        }

        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        public boolean isIgnoreColors() {
            return ignoreColors;
        }

        public boolean isKeepColors() {
            return keepColors;
        }
    }

    public static final class ReplaceResult {
        private final String line;
        private final int occurrences;

        private ReplaceResult(String line, int occurrences) {
            this.line = line;
            this.occurrences = occurrences;
        }

        public String getLine() {
            return line;
        }

        public int getOccurrences() {
            return occurrences;
        }
    }
}
