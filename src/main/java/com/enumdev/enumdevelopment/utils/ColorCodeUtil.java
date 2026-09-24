package com.enumdev.enumdevelopment.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает строку на «цветовые» токены и обычный текст.
 * Поддерживаются все распространённые форматы окраски и градиентов:
 * &x&F&F&0&0&0&0, §x§f§f§0§0§0§0, &#RRGGBB, §#RRGGBB, {#RRGGBB}, <#RRGGBB>,
 * #RRGGBB, теги MiniMessage (&lt;gradient:...&gt;, &lt;red&gt;, &lt;/bold&gt; и т. д.)
 * и классические коды &a / §a.
 */
public final class ColorCodeUtil {

    private static final String NAMED_COLORS =
            "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey"
                    + "|blue|green|aqua|red|light_purple|yellow|white";

    private static final String DECORATIONS =
            "bold|b|italic|i|em|underlined|u|strikethrough|st|obfuscated|obf|reset|r";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)"
                    + "[&\u00a7]x(?:[&\u00a7][0-9a-f]){6}"
                    + "|[&\u00a7]#[0-9a-f]{6}"
                    + "|[{]#[0-9a-f]{6}[}]"
                    + "|<#[0-9a-f]{6}>"
                    + "|</?(?:gradient|rainbow|transition|color|colour|c|font|shadow|key|lang)(?::[^<>]*)?>"
                    + "|</?(?:" + NAMED_COLORS + "|" + DECORATIONS + ")>"
                    + "|#[0-9a-f]{6}(?![0-9a-f])"
                    + "|[&\u00a7][0-9a-fk-or]"
    );

    private static final Pattern RESET_MARKER_PATTERN = Pattern.compile(
            "(?i)"
                    + "[&\u00a7]x(?:[&\u00a7][0-9a-f]){6}"
                    + "|[&\u00a7]#[0-9a-f]{6}"
                    + "|[{]#[0-9a-f]{6}[}]"
                    + "|<#[0-9a-f]{6}>"
                    + "|<(?:gradient|rainbow|transition|color|colour|c)(?::[^<>]*)?>"
                    + "|<(?:" + NAMED_COLORS + ")>"
                    + "|#[0-9a-f]{6}"
                    + "|[&\u00a7][0-9a-fr]"
    );

    private ColorCodeUtil() {
    }

    /**
     * Убирает из строки все цветовые токены.
     */
    public static String strip(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        return TOKEN_PATTERN.matcher(value).replaceAll("");
    }

    public static boolean hasColorCodes(String value) {
        return value != null && !value.isEmpty() && TOKEN_PATTERN.matcher(value).find();
    }

    /**
     * Разбирает строку: отдельно чистый текст и отдельно токены,
     * привязанные к позиции символа чистого текста, перед которым они стоят.
     */
    public static ColorText analyze(String value) {
        String source = value == null ? "" : value;
        StringBuilder plain = new StringBuilder(source.length());
        List<List<String>> markers = new ArrayList<List<String>>();
        markers.add(new ArrayList<String>());

        Matcher matcher = TOKEN_PATTERN.matcher(source);
        int cursor = 0;
        while (matcher.find()) {
            appendPlain(plain, markers, source, cursor, matcher.start());
            markers.get(markers.size() - 1).add(matcher.group());
            cursor = matcher.end();
        }
        appendPlain(plain, markers, source, cursor, source.length());

        return new ColorText(source, plain.toString(), markers);
    }

    private static void appendPlain(StringBuilder plain, List<List<String>> markers, String source, int from, int to) {
        for (int index = from; index < to; index++) {
            plain.append(source.charAt(index));
            markers.add(new ArrayList<String>());
        }
    }

    /**
     * Строка, разобранная на чистый текст и цветовые токены.
     */
    public static final class ColorText {
        private final String original;
        private final String plain;
        private final List<List<String>> markers;

        private ColorText(String original, String plain, List<List<String>> markers) {
            this.original = original;
            this.plain = plain;
            this.markers = markers;
        }

        public String getOriginal() {
            return original;
        }

        public String getPlain() {
            return plain;
        }

        public boolean hasMarkers() {
            for (List<String> group : markers) {
                if (!group.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Токены, стоящие перед символом чистого текста с индексом {@code plainIndex}.
         * Индекс, равный длине чистого текста, хранит токены в конце строки.
         */
        public List<String> markersAt(int plainIndex) {
            if (plainIndex < 0 || plainIndex >= markers.size()) {
                return Collections.emptyList();
            }
            return markers.get(plainIndex);
        }

        /**
         * Переносит токены из диапазона [from, to) чистого текста на новый текст,
         * сохраняя их относительное положение. Так градиент остаётся на месте,
         * а сами буквы меняются.
         */
        public String redistribute(int from, int to, String replacement) {
            String text = replacement == null ? "" : replacement;
            int oldLength = to - from;
            int newLength = text.length();

            List<List<String>> mapped = new ArrayList<List<String>>();
            for (int index = 0; index <= newLength; index++) {
                mapped.add(new ArrayList<String>());
            }

            for (int plainIndex = from; plainIndex < to; plainIndex++) {
                List<String> group = markersAt(plainIndex);
                if (group.isEmpty()) {
                    continue;
                }
                int position = mapPosition(plainIndex - from, oldLength, newLength);
                for (String marker : group) {
                    List<String> slot = mapped.get(position);
                    if (resetsFormatting(marker)) {
                        slot.clear();
                    }
                    slot.add(marker);
                }
            }

            StringBuilder builder = new StringBuilder();
            for (int index = 0; index <= newLength; index++) {
                for (String marker : mapped.get(index)) {
                    builder.append(marker);
                }
                if (index < newLength) {
                    builder.append(text.charAt(index));
                }
            }
            return builder.toString();
        }

        /**
         * Цветовые коды сбрасывают прежнее форматирование, поэтому при сжатии
         * градиента из нескольких кодов подряд смысл имеет только последний.
         */
        private boolean resetsFormatting(String marker) {
            return RESET_MARKER_PATTERN.matcher(marker).matches();
        }

        private int mapPosition(int offset, int oldLength, int newLength) {
            if (offset <= 0 || newLength == 0) {
                return 0;
            }
            if (oldLength <= 0) {
                return 0;
            }
            long scaled = Math.round(offset * (double) newLength / (double) oldLength);
            if (scaled < 0) {
                return 0;
            }
            if (scaled > newLength) {
                return newLength;
            }
            return (int) scaled;
        }
    }
}
