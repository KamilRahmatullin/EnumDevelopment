package com.enumdev.enumdevelopment.managers;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.models.SearchOptions;
import com.enumdev.enumdevelopment.models.UndoEntry;
import com.enumdev.enumdevelopment.models.UndoRestoreResult;
import com.enumdev.enumdevelopment.models.UndoSession;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class UndoManager {

    private static final String METADATA_FILE = "metadata.yml";

    private final Main plugin;
    private final ConfigManager configManager;
    private final Object lock = new Object();

    public UndoManager(Main plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public UndoSession beginSession(SearchOptions options) throws IOException {
        if (!configManager.isUndoEnabled()) {
            return null;
        }

        Path root = getUndoRoot();
        Files.createDirectories(root);

        String id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path sessionDirectory = root.resolve(id).normalize();
        Path backupDirectory = sessionDirectory.resolve("backups").normalize();
        Files.createDirectories(backupDirectory);

        return new UndoSession(id, System.currentTimeMillis(), options, sessionDirectory, backupDirectory);
    }

    public void backupBeforeChange(UndoSession session, Path file, Path rootPath) throws IOException {
        if (session == null) {
            return;
        }

        Path original = file.toAbsolutePath().normalize();
        String backupName = session.nextBackupIndex() + ".bak";
        String relativeBackup = "backups/" + backupName;
        Path backup = session.getBackupDirectory().resolve(backupName).normalize();

        Files.createDirectories(backup.getParent());
        Files.copy(original, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        session.addEntry(new UndoEntry(original, relative(original, rootPath.toAbsolutePath().normalize()), relativeBackup));
    }

    public void completeSession(UndoSession session) throws IOException {
        if (session == null || !session.hasEntries()) {
            discardSession(session);
            return;
        }

        synchronized (lock) {
            writeMetadata(session);
            cleanupOldSessions();
        }
    }

    public void discardSession(UndoSession session) {
        if (session == null) {
            return;
        }
        try {
            deleteDirectory(session.getSessionDirectory());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete undo session " + session.getId() + ": " + exception.getMessage());
        }
    }

    public UndoRestoreResult restoreLatest() {
        synchronized (lock) {
            SessionMetadata metadata = findLatestSession();
            if (metadata == null) {
                return UndoRestoreResult.empty();
            }

            long startedAt = System.currentTimeMillis();
            List<String> errors = new ArrayList<String>();
            int restored = 0;

            for (UndoEntry entry : metadata.entries) {
                try {
                    restoreEntry(metadata.sessionDirectory, entry);
                    restored++;
                } catch (Exception exception) {
                    errors.add(entry.getDisplayPath() + ": " + exception.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startedAt;
            if (errors.isEmpty()) {
                try {
                    deleteDirectory(metadata.sessionDirectory);
                } catch (IOException exception) {
                    errors.add("cleanup: " + exception.getMessage());
                }
            }

            return UndoRestoreResult.completed(metadata.id, restored, metadata.entries.size(), duration, errors);
        }
    }

    private void writeMetadata(UndoSession session) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        SearchOptions options = session.getOptions();

        yaml.set("id", session.getId());
        yaml.set("created-at", session.getCreatedAt());
        yaml.set("completed", true);
        yaml.set("root-path", options.getRootPath().toAbsolutePath().normalize().toString());
        yaml.set("display-path", options.getDisplayPath());
        yaml.set("target", options.getTarget());
        yaml.set("replacement", options.getReplacement());

        List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
        for (UndoEntry entry : session.getEntriesSnapshot()) {
            Map<String, Object> file = new HashMap<String, Object>();
            file.put("path", entry.getOriginalPath().toString());
            file.put("display", entry.getDisplayPath());
            file.put("backup", entry.getBackupPath());
            files.add(file);
        }
        yaml.set("files", files);
        yaml.save(session.getSessionDirectory().resolve(METADATA_FILE).toFile());
    }

    private SessionMetadata findLatestSession() {
        Path root = getUndoRoot();
        if (!Files.isDirectory(root)) {
            return null;
        }

        SessionMetadata latest = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path directory : stream) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                SessionMetadata metadata = readMetadata(directory);
                if (metadata == null || metadata.entries.isEmpty()) {
                    continue;
                }
                if (latest == null || metadata.createdAt > latest.createdAt) {
                    latest = metadata;
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to read undo sessions: " + exception.getMessage());
        }
        return latest;
    }

    private SessionMetadata readMetadata(Path directory) {
        File file = directory.resolve(METADATA_FILE).toFile();
        if (!file.isFile()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.getBoolean("completed", false)) {
            return null;
        }

        String id = yaml.getString("id", directory.getFileName().toString());
        long createdAt = yaml.getLong("created-at", file.lastModified());
        List<UndoEntry> entries = new ArrayList<UndoEntry>();
        for (Map<?, ?> map : yaml.getMapList("files")) {
            Object path = map.get("path");
            Object backup = map.get("backup");
            if (path == null || backup == null) {
                continue;
            }
            Object display = map.get("display");
            entries.add(new UndoEntry(
                    Paths.get(String.valueOf(path)).toAbsolutePath().normalize(),
                    display == null ? String.valueOf(path) : String.valueOf(display),
                    String.valueOf(backup)
            ));
        }
        return new SessionMetadata(id, createdAt, directory.toAbsolutePath().normalize(), entries);
    }

    private void restoreEntry(Path sessionDirectory, UndoEntry entry) throws IOException {
        Path backup = sessionDirectory.resolve(entry.getBackupPath()).normalize();
        if (!backup.startsWith(sessionDirectory)) {
            throw new IOException("backup path is outside undo session");
        }
        if (!Files.isRegularFile(backup)) {
            throw new IOException("backup file is missing");
        }

        Path target = entry.getOriginalPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("target directory is missing");
        }

        Path temp = target.resolveSibling(target.getFileName().toString() + ".enumdev-undo-" + UUID.randomUUID().toString() + ".tmp");
        try {
            Files.copy(backup, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            moveReplacing(temp, target);
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
    }

    private void cleanupOldSessions() {
        Path root = getUndoRoot();
        if (!Files.isDirectory(root)) {
            return;
        }

        List<SessionMetadata> sessions = new ArrayList<SessionMetadata>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path directory : stream) {
                if (Files.isDirectory(directory)) {
                    SessionMetadata metadata = readMetadata(directory);
                    if (metadata != null) {
                        sessions.add(metadata);
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to collect undo sessions: " + exception.getMessage());
            return;
        }

        int maxSessions = configManager.getUndoMaxSessions();
        if (sessions.size() <= maxSessions) {
            return;
        }

        sessions.sort(new Comparator<SessionMetadata>() {
            @Override
            public int compare(SessionMetadata first, SessionMetadata second) {
                return Long.compare(first.createdAt, second.createdAt);
            }
        });

        int removeCount = sessions.size() - maxSessions;
        for (int index = 0; index < removeCount; index++) {
            try {
                deleteDirectory(sessions.get(index).sessionDirectory);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to delete old undo session " + sessions.get(index).id + ": " + exception.getMessage());
            }
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path getUndoRoot() {
        Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path undoRoot = dataDirectory.resolve(configManager.getUndoDirectory()).normalize();
        return undoRoot.startsWith(dataDirectory) ? undoRoot : dataDirectory.resolve("undo").normalize();
    }

    private String relative(Path path, Path root) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    private static final class SessionMetadata {
        private final String id;
        private final long createdAt;
        private final Path sessionDirectory;
        private final List<UndoEntry> entries;

        private SessionMetadata(String id, long createdAt, Path sessionDirectory, List<UndoEntry> entries) {
            this.id = id;
            this.createdAt = createdAt;
            this.sessionDirectory = sessionDirectory;
            this.entries = entries;
        }
    }
}
