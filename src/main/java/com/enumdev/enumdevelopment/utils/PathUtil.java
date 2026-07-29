package com.enumdev.enumdevelopment.utils;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.models.PathResolution;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PathUtil {

    private final Main plugin;
    private final ConfigManager configManager;
    private final Path serverRoot;

    public PathUtil(Main plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.serverRoot = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
    }

    public PathResolution resolve(String input) {
        if (input == null || input.trim().isEmpty()) {
            return PathResolution.failed("invalid-path");
        }

        Path resolved = resolveRaw(input.trim()).toAbsolutePath().normalize();
        if (!configManager.isAllowOutsideServerDirectory() && !resolved.startsWith(serverRoot)) {
            return PathResolution.failed("outside-server-root");
        }
        if (!Files.exists(resolved) || !Files.isReadable(resolved)) {
            return PathResolution.failed("invalid-path");
        }
        return PathResolution.success(resolved);
    }

    public List<String> complete(String input) {
        String current = input == null ? "" : input;
        CompletionBase base = completionBase(current);
        File directory = base.directory.toFile();
        File[] children = directory.listFiles();
        if (children == null) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<String>();
        String partialLower = base.partial.toLowerCase(Locale.ROOT);
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }
            String name = child.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith(partialLower)) {
                continue;
            }
            suggestions.add(base.prefix + name + "/");
        }

        Collections.sort(suggestions, Comparator.naturalOrder());
        return suggestions.size() > 30 ? suggestions.subList(0, 30) : suggestions;
    }

    private Path resolveRaw(String input) {
        if (input.startsWith("/")) {
            return serverRoot.resolve(input.substring(1));
        }

        Path path = Paths.get(input);
        if (path.isAbsolute()) {
            return path;
        }
        return serverRoot.resolve(path);
    }

    private CompletionBase completionBase(String input) {
        String normalized = input.replace('\\', '/');
        boolean rootBased = normalized.startsWith("/");
        String withoutRoot = rootBased ? normalized.substring(1) : normalized;
        int lastSlash = withoutRoot.lastIndexOf('/');
        String folderPart = lastSlash >= 0 ? withoutRoot.substring(0, lastSlash + 1) : "";
        String partial = lastSlash >= 0 ? withoutRoot.substring(lastSlash + 1) : withoutRoot;
        Path directory = serverRoot.resolve(folderPart).normalize();
        String prefix = (rootBased ? "/" : "") + folderPart;
        return new CompletionBase(directory, prefix, partial);
    }

    private static final class CompletionBase {
        private final Path directory;
        private final String prefix;
        private final String partial;

        private CompletionBase(Path directory, String prefix, String partial) {
            this.directory = directory;
            this.prefix = prefix;
            this.partial = partial;
        }
    }
}
