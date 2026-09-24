package com.enumdev.enumdevelopment.commands;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.managers.SearchTaskManager;
import com.enumdev.enumdevelopment.models.PathResolution;
import com.enumdev.enumdevelopment.models.SearchMode;
import com.enumdev.enumdevelopment.models.SearchOptions;
import com.enumdev.enumdevelopment.utils.ArgumentParser;
import com.enumdev.enumdevelopment.utils.ItemDescriptionUtil;
import com.enumdev.enumdevelopment.utils.MessageUtil;
import com.enumdev.enumdevelopment.utils.PathUtil;
import com.enumdev.enumdevelopment.utils.TabCompleteUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EnumDevelopmentCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_USE = "enumdevelopment.use";
    private static final String PERMISSION_FIND = "enumdevelopment.find";
    private static final String PERMISSION_REPLACE = "enumdevelopment.replace";
    private static final String PERMISSION_FIND_ITEM = "enumdevelopment.finditem";
    private static final String PERMISSION_REPLACE_ITEM = "enumdevelopment.replaceitem";
    private static final String PERMISSION_UNDO = "enumdevelopment.undo";
    private static final String PERMISSION_RELOAD = "enumdevelopment.reload";

    private final Main plugin;
    private final ConfigManager configManager;
    private final MessageUtil messageUtil;
    private final SearchTaskManager taskManager;
    private final PathUtil pathUtil;

    public EnumDevelopmentCommand(Main plugin, ConfigManager configManager, MessageUtil messageUtil, SearchTaskManager taskManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageUtil = messageUtil;
        this.taskManager = taskManager;
        this.pathUtil = new PathUtil(plugin, configManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            messageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("help")) {
            sendHelp(sender);
            return true;
        }
        if (subCommand.equals("reload")) {
            handleReload(sender);
            return true;
        }
        if (subCommand.equals("find")) {
            handleFind(sender, args);
            return true;
        }
        if (subCommand.equals("replace")) {
            handleReplace(sender, args);
            return true;
        }
        if (subCommand.equals("finditem")) {
            handleFindItem(sender, args);
            return true;
        }
        if (subCommand.equals("replaceitem")) {
            handleReplaceItem(sender, args);
            return true;
        }
        if (subCommand.equals("undo")) {
            handleUndo(sender);
            return true;
        }

        messageUtil.send(sender, "unknown-command");
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            messageUtil.send(sender, "no-permission");
            return;
        }
        plugin.reloadPlugin();
        messageUtil.send(sender, "reload-success");
    }

    private void handleUndo(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_UNDO)) {
            messageUtil.send(sender, "no-permission");
            return;
        }
        taskManager.startUndoTask(sender);
    }

    private void handleFind(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_FIND)) {
            messageUtil.send(sender, "no-permission");
            return;
        }

        ArgumentParser.ParseResult parsed = ArgumentParser.parse(Arrays.copyOfRange(args, 1, args.length));
        if (!parsed.isSuccess()) {
            messageUtil.send(sender, "unclosed-quote");
            return;
        }

        List<String> tokens = parsed.getTokens();
        if (tokens.size() < 2) {
            messageUtil.send(sender, "invalid-syntax", "usage", "/ed find \"слово или строка\" /plugins [-s] [-n имя] [-f] [-i] [-g]");
            return;
        }

        ParsedFlags flags = parseFlags(sender, tokens, 2);
        if (!flags.valid) {
            return;
        }

        PathResolution resolution = pathUtil.resolve(tokens.get(1));
        if (!resolution.isSuccess()) {
            messageUtil.send(sender, resolution.getMessageKey(), "path", tokens.get(1));
            return;
        }

        SearchOptions options = SearchOptions.builder(SearchMode.FIND)
                .target(tokens.get(0))
                .rootPath(resolution.getPath())
                .displayPath(tokens.get(1))
                .save(flags.save)
                .force(flags.force)
                .fileName(flags.fileName)
                .caseSensitive(flags.caseSensitive)
                .ignoreColors(flags.ignoreColors)
                .keepColors(flags.keepColors)
                .build();

        taskManager.startTask(sender, options);
    }

    private void handleReplace(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_REPLACE)) {
            messageUtil.send(sender, "no-permission");
            return;
        }

        ArgumentParser.ParseResult parsed = ArgumentParser.parse(Arrays.copyOfRange(args, 1, args.length));
        if (!parsed.isSuccess()) {
            messageUtil.send(sender, "unclosed-quote");
            return;
        }

        List<String> tokens = parsed.getTokens();
        if (tokens.size() < 3) {
            messageUtil.send(sender, "invalid-syntax", "usage", "/ed replace \"старое\" \"новое\" /plugins [-s] [-n имя] [-f] [-i] [-g]");
            return;
        }

        ParsedFlags flags = parseFlags(sender, tokens, 3);
        if (!flags.valid) {
            return;
        }

        PathResolution resolution = pathUtil.resolve(tokens.get(2));
        if (!resolution.isSuccess()) {
            messageUtil.send(sender, resolution.getMessageKey(), "path", tokens.get(2));
            return;
        }

        SearchOptions options = SearchOptions.builder(SearchMode.REPLACE)
                .target(tokens.get(0))
                .replacement(tokens.get(1))
                .rootPath(resolution.getPath())
                .displayPath(tokens.get(2))
                .save(flags.save)
                .force(flags.force)
                .fileName(flags.fileName)
                .caseSensitive(flags.caseSensitive)
                .ignoreColors(flags.ignoreColors)
                .keepColors(flags.keepColors)
                .build();

        taskManager.startTask(sender, options);
    }

    private void handleFindItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_FIND_ITEM)) {
            messageUtil.send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player)) {
            messageUtil.send(sender, "only-player");
            return;
        }

        ArgumentParser.ParseResult parsed = ArgumentParser.parse(Arrays.copyOfRange(args, 1, args.length));
        if (!parsed.isSuccess()) {
            messageUtil.send(sender, "unclosed-quote");
            return;
        }

        List<String> tokens = parsed.getTokens();
        if (tokens.size() < 1) {
            messageUtil.send(sender, "invalid-syntax", "usage", "/ed finditem /plugins [-s] [-n имя] [-f]");
            return;
        }

        ParsedFlags flags = parseFlags(sender, tokens, 1);
        if (!flags.valid) {
            return;
        }

        PathResolution resolution = pathUtil.resolve(tokens.get(0));
        if (!resolution.isSuccess()) {
            messageUtil.send(sender, resolution.getMessageKey(), "path", tokens.get(0));
            return;
        }

        ItemStack target = ((Player) sender).getInventory().getItemInMainHand();
        if (isEmpty(target)) {
            messageUtil.send(sender, "empty-main-hand");
            return;
        }

        SearchOptions options = SearchOptions.builder(SearchMode.FIND_ITEM)
                .target(ItemDescriptionUtil.describe(target))
                .targetItem(target)
                .rootPath(resolution.getPath())
                .displayPath(tokens.get(0))
                .save(flags.save)
                .force(flags.force)
                .fileName(flags.fileName)
                .caseSensitive(flags.caseSensitive)
                .ignoreColors(flags.ignoreColors)
                .keepColors(flags.keepColors)
                .build();

        taskManager.startTask(sender, options);
    }

    private void handleReplaceItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_REPLACE_ITEM)) {
            messageUtil.send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player)) {
            messageUtil.send(sender, "only-player");
            return;
        }

        ArgumentParser.ParseResult parsed = ArgumentParser.parse(Arrays.copyOfRange(args, 1, args.length));
        if (!parsed.isSuccess()) {
            messageUtil.send(sender, "unclosed-quote");
            return;
        }

        List<String> tokens = parsed.getTokens();
        if (tokens.size() < 1) {
            messageUtil.send(sender, "invalid-syntax", "usage", "/ed replaceitem /plugins [-s] [-n имя] [-f]");
            return;
        }

        ParsedFlags flags = parseFlags(sender, tokens, 1);
        if (!flags.valid) {
            return;
        }

        PathResolution resolution = pathUtil.resolve(tokens.get(0));
        if (!resolution.isSuccess()) {
            messageUtil.send(sender, resolution.getMessageKey(), "path", tokens.get(0));
            return;
        }

        Player player = (Player) sender;
        ItemStack target = player.getInventory().getItemInMainHand();
        ItemStack replacement = player.getInventory().getItemInOffHand();
        if (isEmpty(target)) {
            messageUtil.send(sender, "empty-main-hand");
            return;
        }
        if (isEmpty(replacement)) {
            messageUtil.send(sender, "empty-off-hand");
            return;
        }

        SearchOptions options = SearchOptions.builder(SearchMode.REPLACE_ITEM)
                .target(ItemDescriptionUtil.describe(target))
                .replacement(ItemDescriptionUtil.describe(replacement))
                .targetItem(target)
                .replacementItem(replacement)
                .rootPath(resolution.getPath())
                .displayPath(tokens.get(0))
                .save(flags.save)
                .force(flags.force)
                .fileName(flags.fileName)
                .caseSensitive(flags.caseSensitive)
                .ignoreColors(flags.ignoreColors)
                .keepColors(flags.keepColors)
                .build();

        taskManager.startTask(sender, options);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private ParsedFlags parseFlags(CommandSender sender, List<String> tokens, int startIndex) {
        ParsedFlags flags = new ParsedFlags();
        String saveFlag = configManager.getFlag("save");
        String forceFlag = configManager.getFlag("force");
        String nameFlag = configManager.getFlag("name");
        String longNameFlag = configManager.getFlag("name-long");
        String ignoreCaseFlag = configManager.getFlag("ignore-case");
        String caseSensitiveFlag = configManager.getFlag("case-sensitive");
        String colorsFlag = configManager.getFlag("colors");
        String stripColorsFlag = configManager.getFlag("strip-colors");

        for (int index = startIndex; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equalsIgnoreCase(saveFlag) || token.equalsIgnoreCase("--save")) {
                flags.save = true;
                continue;
            }
            if (token.equalsIgnoreCase(forceFlag) || token.equalsIgnoreCase("--force")) {
                flags.force = true;
                continue;
            }
            if (matches(token, ignoreCaseFlag, "-i", "--ignore-case")) {
                flags.caseSensitive = Boolean.FALSE;
                continue;
            }
            if (matches(token, caseSensitiveFlag, "-cs", "--case-sensitive")) {
                flags.caseSensitive = Boolean.TRUE;
                continue;
            }
            if (matches(token, colorsFlag, "-g", "--gradient", "--colors")) {
                flags.ignoreColors = true;
                continue;
            }
            if (matches(token, stripColorsFlag, "-gs", "--strip-colors")) {
                flags.ignoreColors = true;
                flags.keepColors = false;
                continue;
            }
            if (token.equalsIgnoreCase(nameFlag) || token.equalsIgnoreCase(longNameFlag) || token.equalsIgnoreCase("--name")) {
                if (index + 1 >= tokens.size()) {
                    messageUtil.send(sender, "missing-flag-value", "flag", token);
                    flags.valid = false;
                    return flags;
                }
                flags.fileName = tokens.get(++index);
                flags.save = true;
                continue;
            }
            messageUtil.send(sender, "invalid-flag", "flag", token);
            flags.valid = false;
            return flags;
        }
        return flags;
    }

    private void sendHelp(CommandSender sender) {
        messageUtil.sendList(sender, "help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<String>();
            subCommands.add("find");
            subCommands.add("replace");
            subCommands.add("finditem");
            subCommands.add("replaceitem");
            subCommands.add("undo");
            subCommands.add("reload");
            subCommands.add("help");
            return TabCompleteUtil.filter(subCommands, args[0]);
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("find")) {
            return completeFind(args);
        }
        if (subCommand.equals("replace")) {
            return completeReplace(args);
        }
        if (subCommand.equals("finditem") || subCommand.equals("replaceitem")) {
            return completeItem(args);
        }
        return Collections.emptyList();
    }

    private List<String> completeFind(String[] args) {
        if (args.length == 2) {
            return Collections.emptyList();
        }
        if (args.length == 3) {
            return pathUtil.complete(args[2]);
        }
        return completeFlags(args, true);
    }

    private List<String> completeReplace(String[] args) {
        if (args.length == 2 || args.length == 3) {
            return Collections.emptyList();
        }
        if (args.length == 4) {
            return pathUtil.complete(args[3]);
        }
        return completeFlags(args, true);
    }

    private List<String> completeItem(String[] args) {
        if (args.length == 2) {
            return pathUtil.complete(args[1]);
        }
        return completeFlags(args, false);
    }

    private List<String> completeFlags(String[] args, boolean textMode) {
        String current = args[args.length - 1];
        String previous = args.length >= 2 ? args[args.length - 2] : "";
        if (previous.equalsIgnoreCase(configManager.getFlag("name"))
                || previous.equalsIgnoreCase(configManager.getFlag("name-long"))
                || previous.equalsIgnoreCase("--name")) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<String>();
        addFlagIfAbsent(args, suggestions, configManager.getFlag("save"));
        addFlagIfAbsent(args, suggestions, configManager.getFlag("name"));
        addFlagIfAbsent(args, suggestions, configManager.getFlag("name-long"));
        addFlagIfAbsent(args, suggestions, configManager.getFlag("force"));
        if (textMode) {
            addFlagIfAbsent(args, suggestions, defaultFlag(configManager.getFlag("ignore-case"), "-i"));
            addFlagIfAbsent(args, suggestions, defaultFlag(configManager.getFlag("case-sensitive"), "-cs"));
            addFlagIfAbsent(args, suggestions, defaultFlag(configManager.getFlag("colors"), "-g"));
            addFlagIfAbsent(args, suggestions, defaultFlag(configManager.getFlag("strip-colors"), "-gs"));
        }
        return TabCompleteUtil.filter(suggestions, current);
    }

    private String defaultFlag(String configured, String fallback) {
        return configured == null || configured.isEmpty() ? fallback : configured;
    }

    private void addFlagIfAbsent(String[] args, List<String> suggestions, String flag) {
        if (flag == null || flag.isEmpty()) {
            return;
        }
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return;
            }
        }
        suggestions.add(flag);
    }

    private boolean matches(String token, String configuredFlag, String... aliases) {
        if (configuredFlag != null && !configuredFlag.isEmpty() && token.equalsIgnoreCase(configuredFlag)) {
            return true;
        }
        for (String alias : aliases) {
            if (token.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }

    private static final class ParsedFlags {
        private boolean valid = true;
        private boolean save;
        private boolean force;
        private String fileName;
        private Boolean caseSensitive;
        private boolean ignoreColors;
        private boolean keepColors = true;
    }
}
