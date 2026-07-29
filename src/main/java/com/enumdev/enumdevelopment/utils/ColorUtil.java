package com.enumdev.enumdevelopment.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)(?:&?#)([0-9a-f]{6})");

    private ColorUtil() {
    }

    public static String colorize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
