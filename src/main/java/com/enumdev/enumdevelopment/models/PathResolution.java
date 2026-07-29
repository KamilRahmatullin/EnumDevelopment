package com.enumdev.enumdevelopment.models;

import java.nio.file.Path;

public final class PathResolution {

    private final boolean success;
    private final Path path;
    private final String messageKey;

    private PathResolution(boolean success, Path path, String messageKey) {
        this.success = success;
        this.path = path;
        this.messageKey = messageKey;
    }

    public static PathResolution success(Path path) {
        return new PathResolution(true, path, "");
    }

    public static PathResolution failed(String messageKey) {
        return new PathResolution(false, null, messageKey);
    }

    public boolean isSuccess() {
        return success;
    }

    public Path getPath() {
        return path;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
