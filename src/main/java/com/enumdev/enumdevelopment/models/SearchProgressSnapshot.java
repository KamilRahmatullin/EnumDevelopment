package com.enumdev.enumdevelopment.models;

public final class SearchProgressSnapshot {

    private final long elapsedMillis;
    private final long totalFiles;
    private final long scannedFiles;
    private final long matchedFiles;
    private final long skippedFiles;
    private final long totalMatches;
    private final long totalReplacements;
    private final String currentFile;

    public SearchProgressSnapshot(long elapsedMillis, long totalFiles, long scannedFiles, long matchedFiles, long skippedFiles,
                                  long totalMatches, long totalReplacements, String currentFile) {
        this.elapsedMillis = elapsedMillis;
        this.totalFiles = totalFiles;
        this.scannedFiles = scannedFiles;
        this.matchedFiles = matchedFiles;
        this.skippedFiles = skippedFiles;
        this.totalMatches = totalMatches;
        this.totalReplacements = totalReplacements;
        this.currentFile = currentFile;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getScannedFiles() {
        return scannedFiles;
    }

    public long getMatchedFiles() {
        return matchedFiles;
    }

    public long getSkippedFiles() {
        return skippedFiles;
    }

    public long getTotalMatches() {
        return totalMatches;
    }

    public long getTotalReplacements() {
        return totalReplacements;
    }

    public String getCurrentFile() {
        return currentFile;
    }
}
