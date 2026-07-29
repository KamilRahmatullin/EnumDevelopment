package com.enumdev.enumdevelopment.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UndoRestoreResult {

    private final boolean sessionFound;
    private final String sessionId;
    private final int restoredFiles;
    private final int totalFiles;
    private final long durationMillis;
    private final List<String> errors;

    private UndoRestoreResult(boolean sessionFound, String sessionId, int restoredFiles, int totalFiles, long durationMillis, List<String> errors) {
        this.sessionFound = sessionFound;
        this.sessionId = sessionId;
        this.restoredFiles = restoredFiles;
        this.totalFiles = totalFiles;
        this.durationMillis = durationMillis;
        this.errors = errors == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(errors));
    }

    public static UndoRestoreResult empty() {
        return new UndoRestoreResult(false, "", 0, 0, 0L, Collections.<String>emptyList());
    }

    public static UndoRestoreResult completed(String sessionId, int restoredFiles, int totalFiles, long durationMillis, List<String> errors) {
        return new UndoRestoreResult(true, sessionId, restoredFiles, totalFiles, durationMillis, errors);
    }

    public boolean isSessionFound() {
        return sessionFound;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getRestoredFiles() {
        return restoredFiles;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public List<String> getErrors() {
        return errors;
    }
}
