package com.enumdev.enumdevelopment.models;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class CommandTarget {

    private final boolean player;
    private final UUID playerId;

    private CommandTarget(boolean player, UUID playerId) {
        this.player = player;
        this.playerId = playerId;
    }

    public static CommandTarget from(CommandSender sender) {
        if (sender instanceof Player) {
            return new CommandTarget(true, ((Player) sender).getUniqueId());
        }
        return new CommandTarget(false, null);
    }

    public CommandSender resolve() {
        if (player) {
            return Bukkit.getPlayer(playerId);
        }
        return Bukkit.getConsoleSender();
    }
}
