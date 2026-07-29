package com.enumdev.enumdevelopment.models;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchReport {

    private final SearchMode mode;
    private final Path rootPath;
    private final String target;
    private final String replacement;
    private final int maxStoredResults;
    private final long startedAt;
    private final List<SearchResult> results = new ArrayList<SearchResult>();
    private final List<String> errors = new ArrayList<String>();

    private long durationMillis;
    private long totalFiles;
    private long scannedFiles;
    private long matchedFiles;
    private long skippedFiles;
    private long totalMatches;
    private long totalReplacements;
    private boolean truncated;

    public SearchReport(SearchMode mode, Path rootPath, String target, String replacement, int maxStoredResults) {
        this.mode = mode;
        this.rootPath = rootPath;
        this.target = target;
        this.replacement = replacement;
        this.maxStoredResults = maxStoredResults;
        this.startedAt = System.currentTimeMillis();
    }

    public void finish() {
        this.durationMillis = System.currentTimeMillis() - startedAt;
    }

    public SearchProgressSnapshot snapshot(String currentFile) {
        return new SearchProgressSnapshot(
                System.currentTimeMillis() - startedAt,
                totalFiles,
                scannedFiles,
                matchedFiles,
                skippedFiles,
                totalMatches,
                totalReplacements,
                currentFile
        );
    }

    public void addTotalFile() {
        totalFiles++;
    }

    public void addScannedFile() {
        scannedFiles++;
    }

    public void addMatchedFile() {
        matchedFiles++;
    }

    public void addSkippedFile() {
        skippedFiles++;
    }

    public void addMatches(long amount) {
        totalMatches += amount;
    }

    public void addReplacements(long amount) {
        totalReplacements += amount;
    }

    public void addResult(SearchResult result) {
        if (results.size() < maxStoredResults) {
            results.add(result);
            return;
        }
        truncated = true;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public SearchMode getMode() {
        return mode;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public String getTarget() {
        return target;
    }

    public String getReplacement() {
        return replacement;
    }

    public long getDurationMillis() {
        return durationMillis;
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

    public boolean isTruncated() {
        return truncated;
    }

    public List<SearchResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
