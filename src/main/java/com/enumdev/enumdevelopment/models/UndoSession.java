package com.enumdev.enumdevelopment.models;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class UndoSession {

    private final String id;
    private final long createdAt;
    private final SearchOptions options;
    private final Path sessionDirectory;
    private final Path backupDirectory;
    private final AtomicInteger backupCounter = new AtomicInteger(0);
    private final List<UndoEntry> entries = Collections.synchronizedList(new ArrayList<UndoEntry>());

    public UndoSession(String id, long createdAt, SearchOptions options, Path sessionDirectory, Path backupDirectory) {
        this.id = id;
        this.createdAt = createdAt;
        this.options = options;
        this.sessionDirectory = sessionDirectory;
        this.backupDirectory = backupDirectory;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public SearchOptions getOptions() {
        return options;
    }

    public Path getSessionDirectory() {
        return sessionDirectory;
    }

    public Path getBackupDirectory() {
        return backupDirectory;
    }

    public int nextBackupIndex() {
        return backupCounter.getAndIncrement();
    }

    public void addEntry(UndoEntry entry) {
        entries.add(entry);
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public List<UndoEntry> getEntriesSnapshot() {
        synchronized (entries) {
            return new ArrayList<UndoEntry>(entries);
        }
    }
}
