package com.enumdev.enumdevelopment.config;

import com.enumdev.enumdevelopment.Main;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;

    private boolean caseSensitive;
    private int maxChatResults;
    private int maxStoredResults;
    private int maxActiveTasks;
    private long maxFileSizeBytes;
    private boolean followSymbolicLinks;
    private boolean searchBinaryFiles;
    private boolean allowOutsideServerDirectory;
    private String resultDirectory;
    private Charset charset;
    private int maxDepth;
    private boolean progressEnabled;
    private int progressIntervalFiles;
    private long progressIntervalMillis;
    private List<String> allowedExtensions;
    private List<String> ignoredDirectories;
    private boolean undoEnabled;
    private String undoDirectory;
    private int undoMaxSessions;
    private boolean itemSearchMatchAmount;
    private boolean itemSearchScanBase64;
    private boolean itemSearchPreserveAmountOnReplace;
    private int itemSearchBase64MinLength;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.config = plugin.getConfig();
        addDefaults();
        config.options().copyDefaults(true);
        plugin.saveConfig();

        this.caseSensitive = config.getBoolean("settings.case-sensitive", true);
        this.maxChatResults = Math.max(1, config.getInt("settings.max-chat-results", 15));
        this.maxStoredResults = Math.max(1, config.getInt("settings.max-stored-results", 10000));
        this.maxActiveTasks = Math.max(1, config.getInt("settings.max-active-tasks", 4));
        this.maxFileSizeBytes = Math.max(1, config.getLong("settings.max-file-size-kb", 4096L)) * 1024L;
        this.maxDepth = Math.max(1, config.getInt("settings.max-depth", 32));
        this.followSymbolicLinks = config.getBoolean("settings.follow-symbolic-links", false);
        this.searchBinaryFiles = config.getBoolean("settings.search-binary-files", false);
        this.allowOutsideServerDirectory = config.getBoolean("settings.allow-outside-server-directory", false);
        this.progressEnabled = config.getBoolean("settings.progress.enabled", true);
        this.progressIntervalFiles = Math.max(1, config.getInt("settings.progress.interval-files", 250));
        this.progressIntervalMillis = Math.max(500L, config.getLong("settings.progress.interval-ms", 3000L));
        this.resultDirectory = config.getString("settings.result-directory", "results");
        this.charset = parseCharset(config.getString("settings.charset", "UTF-8"));
        this.allowedExtensions = normalizeExtensions(config.getStringList("settings.allowed-extensions"));
        this.ignoredDirectories = normalizeNames(config.getStringList("settings.ignored-directories"));
        this.undoEnabled = config.getBoolean("settings.undo.enabled", true);
        this.undoDirectory = config.getString("settings.undo.directory", "undo");
        this.undoMaxSessions = Math.max(1, config.getInt("settings.undo.max-sessions", 10));
        this.itemSearchMatchAmount = config.getBoolean("settings.item-search.match-amount", false);
        this.itemSearchScanBase64 = config.getBoolean("settings.item-search.scan-base64", true);
        this.itemSearchPreserveAmountOnReplace = config.getBoolean("settings.item-search.preserve-amount-on-replace", true);
        this.itemSearchBase64MinLength = Math.max(24, config.getInt("settings.item-search.base64-min-length", 48));
    }

    private void addDefaults() {
        config.addDefault("settings.case-sensitive", true);
        config.addDefault("settings.max-chat-results", 15);
        config.addDefault("settings.max-stored-results", 10000);
        config.addDefault("settings.max-active-tasks", 4);
        config.addDefault("settings.max-file-size-kb", 4096);
        config.addDefault("settings.max-depth", 32);
        config.addDefault("settings.follow-symbolic-links", false);
        config.addDefault("settings.search-binary-files", false);
        config.addDefault("settings.allow-outside-server-directory", false);
        config.addDefault("settings.progress.enabled", true);
        config.addDefault("settings.progress.interval-files", 250);
        config.addDefault("settings.progress.interval-ms", 3000);
        config.addDefault("settings.result-directory", "results");
        config.addDefault("settings.charset", "UTF-8");
        config.addDefault("settings.allowed-extensions", defaultAllowedExtensions());
        config.addDefault("settings.ignored-directories", defaultIgnoredDirectories());
        config.addDefault("settings.undo.enabled", true);
        config.addDefault("settings.undo.directory", "undo");
        config.addDefault("settings.undo.max-sessions", 10);
        config.addDefault("settings.item-search.match-amount", false);
        config.addDefault("settings.item-search.scan-base64", true);
        config.addDefault("settings.item-search.preserve-amount-on-replace", true);
        config.addDefault("settings.item-search.base64-min-length", 48);
        config.addDefault("flags.save", "-s");
        config.addDefault("flags.force", "-f");
        config.addDefault("flags.name", "-n");
        config.addDefault("flags.name-long", "-name");
        config.addDefault("flags.ignore-case", "-i");
        config.addDefault("flags.case-sensitive", "-cs");
        config.addDefault("flags.colors", "-g");
        config.addDefault("flags.strip-colors", "-gs");
        config.addDefault("messages.prefix", "&#44D7B6&lEnumDevelopment &8» ");
        config.addDefault("messages.no-permission", "&cУ вас нет прав для выполнения этой команды.");
        config.addDefault("messages.only-player", "&cЭта команда доступна только игроку.");
        config.addDefault("messages.unknown-command", "&cНеизвестная подкоманда. Используйте &f/ed help&c.");
        config.addDefault("messages.invalid-syntax", "&cНеверный синтаксис. Используйте: &f{usage}");
        config.addDefault("messages.invalid-path", "&cПуть не найден или недоступен: &f{path}");
        config.addDefault("messages.outside-server-root", "&cЭтот путь выходит за пределы папки сервера.");
        config.addDefault("messages.unclosed-quote", "&cВ команде не закрыта кавычка.");
        config.addDefault("messages.invalid-flag", "&cНеизвестный флаг: &f{flag}");
        config.addDefault("messages.missing-flag-value", "&cДля флага &f{flag} &cнужно указать значение.");
        config.addDefault("messages.empty-main-hand", "&cВозьмите искомый предмет в основную руку.");
        config.addDefault("messages.empty-off-hand", "&cВозьмите предмет-замену во вторую руку.");
        config.addDefault("messages.task-limit", "&cСейчас выполняется слишком много задач. Попробуйте позже.");
        config.addDefault("messages.task-started-find", "&7Поиск &f{target} &7в &f{path} &7запущен асинхронно.");
        config.addDefault("messages.task-started-replace", "&7Замена &f{target} &7на &f{replacement} &7в &f{path} &7запущена асинхронно.");
        config.addDefault("messages.task-started-finditem", "&7Поиск предмета &f{target} &7в &f{path} &7запущен асинхронно.");
        config.addDefault("messages.task-started-replaceitem", "&7Замена предмета &f{target} &7на &f{replacement} &7в &f{path} &7запущена асинхронно.");
        config.addDefault("messages.task-started-undo", "&7Отмена последней замены запущена асинхронно.");
        config.addDefault("messages.task-progress", "&7Обработка: всего &f{total}&7, проверено &f{scanned}&7, пропущено &f{skipped}&7, совпадений &f{matches}&7, файл &f{file}&7.");
        config.addDefault("messages.task-failed", "&cЗадача завершилась с ошибкой: &f{error}");
        config.addDefault("messages.task-cancelled", "&cЗадача была отменена.");
        config.addDefault("messages.search-finished", "&aПоиск завершён за &f{time}мс&a. Всего файлов: &f{total}&a, проверено: &f{files}&a, пропущено: &f{skipped}&a, совпадений: &f{matches}&a.");
        config.addDefault("messages.replace-finished", "&aЗамена завершена за &f{time}мс&a. Всего файлов: &f{total}&a, проверено: &f{files}&a, пропущено: &f{skipped}&a, замен: &f{replacements}&a.");
        config.addDefault("messages.no-results", "&eСовпадений не найдено.");
        config.addDefault("messages.result-line-find", "&8- &f{file}&7:&f{line} &8→ &7{content}");
        config.addDefault("messages.result-line-replace", "&8- &f{file}&7:&f{line} &8→ &7{before} &8=> &a{after}");
        config.addDefault("messages.results-truncated", "&eРезультаты в памяти ограничены: показано/сохранено &f{stored}&e из &f{total}&e. Увеличьте settings.max-stored-results при необходимости.");
        config.addDefault("messages.errors-detected", "&eНекоторые файлы не удалось обработать: &f{errors}&e. Подробности есть в сохранённом файле, если включён флаг сохранения.");
        config.addDefault("messages.file-exists", "&cФайл результатов уже существует: &f{file}&c. Добавьте &f-f&c для перезаписи.");
        config.addDefault("messages.results-saved", "&aРезультаты сохранены в файл: &f{file}");
        config.addDefault("messages.undo-available", "&7Для отмены этой замены используйте &f/ed undo&7.");
        config.addDefault("messages.undo-none", "&eНет сохранённых изменений для отмены.");
        config.addDefault("messages.undo-finished", "&aОтмена завершена за &f{time}мс&a. Восстановлено файлов: &f{restored}&a/&f{total}&a.");
        config.addDefault("messages.undo-partial", "&eОтмена выполнена частично за &f{time}мс&e. Восстановлено файлов: &f{restored}&e/&f{total}&e, ошибок: &f{errors}&e.");
        config.addDefault("messages.reload-success", "&aКонфигурация EnumDevelopment перезагружена.");
        List<String> help = new ArrayList<String>();
        help.add("&#44D7B6&lEnumDevelopment &8— &7помощь");
        help.add("&f/ed find \"строка\" /plugins &8или &f/ed find 'JSON/строка' /plugins &8[-s] [-n имя] [-f] [-i] [-g]");
        help.add("&f/ed replace \"старое\" \"новое\" /plugins &8или &f/ed replace 'старое JSON' 'новое JSON' /plugins &8[-s] [-n имя] [-f] [-i] [-g]");
        help.add("&f/ed finditem /plugins &8[-s] [-n имя] [-f] &8— &7найти предмет из основной руки");
        help.add("&f/ed replaceitem /plugins &8[-s] [-n имя] [-f] &8— &7заменить предмет из основной руки на предмет из второй руки");
        help.add("&f/ed undo &8— &7отменить последнюю замену");
        help.add("&f/ed reload");
        help.add("&7Строки можно оборачивать в &f\"двойные\" &7или &f'одинарные' &7кавычки.");
        help.add("&7Флаги: &f-s &7сохранить, &f-n/-name &7имя файла, &f-f &7перезаписать.");
        help.add("&7Флаги поиска: &f-i &7игнорировать регистр, &f-cs &7учитывать регистр.");
        help.add("&7&f-g &7— игнорировать цвета и градиенты любого формата; при замене градиент сохраняется.");
        help.add("&7&f-gs &7— то же, но новый текст вставляется без цветов.");
        config.addDefault("messages.help", help);
    }

    private List<String> defaultAllowedExtensions() {
        List<String> extensions = new ArrayList<String>();
        extensions.add(".yml");
        extensions.add(".yaml");
        extensions.add(".json");
        extensions.add(".txt");
        extensions.add(".conf");
        extensions.add(".config");
        extensions.add(".properties");
        extensions.add(".lang");
        extensions.add(".ini");
        extensions.add(".csv");
        extensions.add(".sk");
        extensions.add(".js");
        extensions.add(".html");
        return extensions;
    }

    private List<String> defaultIgnoredDirectories() {
        List<String> directories = new ArrayList<String>();
        directories.add("cache");
        directories.add("caches");
        directories.add("logs");
        directories.add("log");
        directories.add("backup");
        directories.add("backups");
        directories.add("libraries");
        directories.add("database");
        directories.add("databases");
        directories.add("storage");
        return directories;
    }

    private Charset parseCharset(String value) {
        try {
            return Charset.forName(value);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private List<String> normalizeExtensions(List<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.unmodifiableList(defaultAllowedExtensions());
        }
        List<String> normalized = new ArrayList<String>();
        for (String extension : input) {
            if (extension == null || extension.trim().isEmpty()) {
                continue;
            }
            String value = extension.trim().toLowerCase();
            if (value.equals("*")) {
                return Collections.emptyList();
            }
            normalized.add(value.startsWith(".") ? value : "." + value);
        }
        if (normalized.isEmpty()) {
            return Collections.unmodifiableList(defaultAllowedExtensions());
        }
        return Collections.unmodifiableList(normalized);
    }

    private List<String> normalizeNames(List<String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<String>();
        for (String name : input) {
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            normalized.add(name.trim().toLowerCase());
        }
        return Collections.unmodifiableList(normalized);
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "");
    }

    public List<String> getMessageList(String path) {
        return config.getStringList("messages." + path);
    }

    public String getFlag(String path) {
        return config.getString("flags." + path, "");
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public int getMaxChatResults() {
        return maxChatResults;
    }

    public int getMaxStoredResults() {
        return maxStoredResults;
    }

    public int getMaxActiveTasks() {
        return maxActiveTasks;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public boolean isFollowSymbolicLinks() {
        return followSymbolicLinks;
    }

    public boolean isSearchBinaryFiles() {
        return searchBinaryFiles;
    }

    public boolean isAllowOutsideServerDirectory() {
        return allowOutsideServerDirectory;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean isProgressEnabled() {
        return progressEnabled;
    }

    public int getProgressIntervalFiles() {
        return progressIntervalFiles;
    }

    public long getProgressIntervalMillis() {
        return progressIntervalMillis;
    }

    public String getResultDirectory() {
        return resultDirectory;
    }

    public Charset getCharset() {
        return charset;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public List<String> getIgnoredDirectories() {
        return ignoredDirectories;
    }

    public boolean isUndoEnabled() {
        return undoEnabled;
    }

    public String getUndoDirectory() {
        return undoDirectory;
    }

    public int getUndoMaxSessions() {
        return undoMaxSessions;
    }

    public boolean isItemSearchMatchAmount() {
        return itemSearchMatchAmount;
    }

    public boolean isItemSearchScanBase64() {
        return itemSearchScanBase64;
    }

    public boolean isItemSearchPreserveAmountOnReplace() {
        return itemSearchPreserveAmountOnReplace;
    }

    public int getItemSearchBase64MinLength() {
        return itemSearchBase64MinLength;
    }
}

