package com.enumdev.enumdevelopment.internal;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.commands.CommandRegistry;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.managers.SearchTaskManager;
import com.enumdev.enumdevelopment.managers.UndoManager;
import com.enumdev.enumdevelopment.utils.MessageUtil;
import org.bukkit.event.HandlerList;

public final class RuntimeEntrypoint {
    private final Main plugin;
    private ConfigManager configManager;
    private MessageUtil messageUtil;
    private SearchTaskManager searchTaskManager;
    private UndoManager undoManager;
    private CommandRegistry commandRegistry;

    public RuntimeEntrypoint(Main plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.saveDefaultConfig();
        configManager = new ConfigManager(plugin);
        configManager.load();
        messageUtil = new MessageUtil(configManager);
        undoManager = new UndoManager(plugin, configManager);
        searchTaskManager = new SearchTaskManager(plugin, configManager, messageUtil, undoManager);
        commandRegistry = new CommandRegistry(plugin, configManager, messageUtil, searchTaskManager);
        commandRegistry.register();
        plugin.getLogger().info("EnumDevelopment enabled. Author: Jasper");
    }

    public void disable() {
        if (commandRegistry != null) commandRegistry.unregister();
        if (searchTaskManager != null) searchTaskManager.cancelAll();
        HandlerList.unregisterAll(plugin);
        plugin.getLogger().info("EnumDevelopment disabled.");
    }

    public void reloadPlugin() {
        plugin.reloadConfig();
        configManager.load();
    }
}
