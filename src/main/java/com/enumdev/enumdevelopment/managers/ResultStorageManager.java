package com.enumdev.enumdevelopment.managers;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.models.SearchReport;
import com.enumdev.enumdevelopment.models.SearchResult;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResultStorageManager {

    private final Main plugin;
    private final ConfigManager configManager;

    public ResultStorageManager(Main plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public Path save(SearchReport report, String requestedName, boolean force) throws IOException {
        Path directory = plugin.getDataFolder().toPath().resolve(configManager.getResultDirectory()).normalize();
        Files.createDirectories(directory);

        String fileName = requestedName == null || requestedName.trim().isEmpty()
                ? createAutomaticName(report)
                : sanitizeFileName(requestedName);

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            fileName += ".yml";
        }

        Path file = directory.resolve(fileName).normalize();
        if (!file.startsWith(directory)) {
            throw new IOException("Invalid file name");
        }
        if (Files.exists(file) && !force) {
            throw new FileAlreadyExistsResultException(file);
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("meta.created-at", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        yaml.set("meta.mode", report.getMode().name().toLowerCase(Locale.ROOT));
        yaml.set("meta.root", report.getRootPath().toString());
        yaml.set("meta.target", report.getTarget());
        yaml.set("meta.replacement", report.getReplacement());
        yaml.set("summary.duration-ms", report.getDurationMillis());
        yaml.set("summary.total-files", report.getTotalFiles());
        yaml.set("summary.scanned-files", report.getScannedFiles());
        yaml.set("summary.matched-files", report.getMatchedFiles());
        yaml.set("summary.skipped-files", report.getSkippedFiles());
        yaml.set("summary.total-matches", report.getTotalMatches());
        yaml.set("summary.total-replacements", report.getTotalReplacements());
        yaml.set("summary.truncated", report.isTruncated());
        yaml.set("errors", report.getErrors());
        yaml.set("results", serializeResults(report));
        yaml.save(file.toFile());
        return file;
    }

    private String createAutomaticName(SearchReport report) {
        String prefix = report.getMode().name().toLowerCase(Locale.ROOT);
        String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        return prefix + "_" + time + ".yml";
    }

    private String sanitizeFileName(String value) {
        return value.trim().replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ._-]", "_");
    }

    private List<Map<String, Object>> serializeResults(SearchReport report) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (SearchResult result : report.getResults()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("file", result.getFile());
            entry.put("line", result.getLine());
            entry.put("occurrences", result.getOccurrences());
            entry.put("content", result.getContent());
            entry.put("before", result.getBefore());
            entry.put("after", result.getAfter());
            list.add(entry);
        }
        return list;
    }

    public static final class FileAlreadyExistsResultException extends IOException {
        private final Path file;

        public FileAlreadyExistsResultException(Path file) {
            super(file.toString());
            this.file = file;
        }

        public Path getFile() {
            return file;
        }
    }
}
