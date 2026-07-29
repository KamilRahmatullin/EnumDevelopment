package com.enumdev.enumdevelopment.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArgumentParser {

    private ArgumentParser() {
    }

    public static ParseResult parse(String[] args) {
        String raw = join(args);
        List<String> tokens = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaping = false;

        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);

            if (escaping) {
                current.append(character);
                escaping = false;
                continue;
            }

            if (character == '\\') {
                escaping = true;
                continue;
            }

            if (quoted) {
                if (character == quote && isQuoteClosing(raw, index)) {
                    quoted = false;
                    quote = 0;
                    continue;
                }
                current.append(character);
                continue;
            }

            if (Character.isWhitespace(character)) {
                push(tokens, current);
                continue;
            }

            if ((character == '"' || character == '\'') && current.length() == 0) {
                quoted = true;
                quote = character;
                continue;
            }

            current.append(character);
        }

        if (escaping) {
            current.append('\\');
        }

        if (quoted) {
            return ParseResult.failed();
        }

        push(tokens, current);
        return ParseResult.success(tokens);
    }

    private static boolean isQuoteClosing(String raw, int index) {
        return index + 1 >= raw.length() || Character.isWhitespace(raw.charAt(index + 1));
    }

    private static String join(String[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < args.length; index++) {
            if (index > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private static void push(List<String> tokens, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }

    public static final class ParseResult {
        private final boolean success;
        private final List<String> tokens;

        private ParseResult(boolean success, List<String> tokens) {
            this.success = success;
            this.tokens = tokens;
        }

        public static ParseResult success(List<String> tokens) {
            return new ParseResult(true, Collections.unmodifiableList(tokens));
        }

        public static ParseResult failed() {
            return new ParseResult(false, Collections.<String>emptyList());
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getTokens() {
            return tokens;
        }
    }
}
