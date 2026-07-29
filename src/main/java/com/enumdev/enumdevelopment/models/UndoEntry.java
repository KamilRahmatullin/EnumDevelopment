package com.enumdev.enumdevelopment.models;

import java.nio.file.Path;

public final class UndoEntry {

    private final Path originalPath;
    private final String displayPath;
    private final String backupPath;

    public UndoEntry(Path originalPath, String displayPath, String backupPath) {
        this.originalPath = originalPath;
        this.displayPath = displayPath;
        this.backupPath = backupPath;
    }

    public Path getOriginalPath() {
        return originalPath;
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public String getBackupPath() {
        return backupPath;
    }
}
