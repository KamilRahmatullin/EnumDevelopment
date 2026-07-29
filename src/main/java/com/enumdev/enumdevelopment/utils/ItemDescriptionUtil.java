package com.enumdev.enumdevelopment.utils;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemDescriptionUtil {

    private ItemDescriptionUtil() {
    }

    public static String describe(ItemStack item) {
        if (item == null) {
            return "AIR";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(item.getType().name()).append(" x").append(item.getAmount());

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null) {
            return builder.toString();
        }
        if (meta.hasDisplayName()) {
            builder.append(" name=").append(clean(meta.getDisplayName()));
        }
        if (meta.hasLore() && meta.getLore() != null) {
            builder.append(" lore=").append(meta.getLore().size());
        }
        if (!meta.getEnchants().isEmpty()) {
            builder.append(" enchants=").append(meta.getEnchants().size());
        }
        if (meta.isUnbreakable()) {
            builder.append(" unbreakable");
        }
        return builder.toString();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(value);
        return stripped == null ? value : stripped;
    }
}
