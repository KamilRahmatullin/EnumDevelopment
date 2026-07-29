package com.enumdev.enumdevelopment.models;

import java.nio.file.Path;

public final class SearchTaskResult {

    private final SearchReport report;
    private final Path savedFile;
    private final boolean undoAvailable;

    public SearchTaskResult(SearchReport report, Path savedFile, boolean undoAvailable) {
        this.report = report;
        this.savedFile = savedFile;
        this.undoAvailable = undoAvailable;
    }

    public SearchReport getReport() {
        return report;
    }

    public Path getSavedFile() {
        return savedFile;
    }

    public boolean isUndoAvailable() {
        return undoAvailable;
    }
}
