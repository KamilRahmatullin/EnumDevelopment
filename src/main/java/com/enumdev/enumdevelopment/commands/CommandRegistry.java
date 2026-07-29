package com.enumdev.enumdevelopment.commands;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.managers.SearchTaskManager;
import com.enumdev.enumdevelopment.utils.MessageUtil;
import org.bukkit.command.PluginCommand;

public final class CommandRegistry {

    private final Main plugin;
    private final EnumDevelopmentCommand command;

    public CommandRegistry(Main plugin, ConfigManager configManager, MessageUtil messageUtil, SearchTaskManager taskManager) {
        this.plugin = plugin;
        this.command = new EnumDevelopmentCommand(plugin, configManager, messageUtil, taskManager);
    }

    public void register() {
        PluginCommand pluginCommand = plugin.getCommand("ed");
        if (pluginCommand == null) {
            plugin.getLogger().severe("Command /ed is not declared in plugin.yml.");
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    public void unregister() {
        PluginCommand pluginCommand = plugin.getCommand("ed");
        if (pluginCommand == null) {
            return;
        }
        pluginCommand.setExecutor(null);
        pluginCommand.setTabCompleter(null);
    }
}
