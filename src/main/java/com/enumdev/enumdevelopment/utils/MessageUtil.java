package com.enumdev.enumdevelopment.utils;

import com.enumdev.enumdevelopment.config.ConfigManager;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageUtil {

    private final ConfigManager configManager;

    public MessageUtil(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void send(CommandSender sender, String key, String... replacements) {
        Map<String, String> placeholders = new HashMap<String, String>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            placeholders.put(replacements[index], replacements[index + 1]);
        }
        sendRaw(sender, key, placeholders);
    }

    public void sendRaw(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = configManager.getMessage("prefix") + configManager.getMessage(key);
        sender.sendMessage(ColorUtil.colorize(apply(message, placeholders)));
    }

    public void sendList(CommandSender sender, String key) {
        List<String> messages = configManager.getMessageList(key);
        for (String message : messages) {
            sender.sendMessage(ColorUtil.colorize(apply(message, new HashMap<String, String>())));
        }
    }

    private String apply(String message, Map<String, String> placeholders) {
        String result = message == null ? "" : message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
