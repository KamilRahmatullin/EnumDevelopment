package com.enumdev.enumdevelopment.models;

import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;

public final class SearchOptions {

    private final SearchMode mode;
    private final String target;
    private final String replacement;
    private final ItemStack targetItem;
    private final ItemStack replacementItem;
    private final Path rootPath;
    private final String displayPath;
    private final boolean save;
    private final boolean force;
    private final String fileName;
    private final Boolean caseSensitive;
    private final boolean ignoreColors;
    private final boolean keepColors;

    private SearchOptions(Builder builder) {
        this.mode = builder.mode;
        this.target = builder.target;
        this.replacement = builder.replacement;
        this.targetItem = builder.targetItem == null ? null : builder.targetItem.clone();
        this.replacementItem = builder.replacementItem == null ? null : builder.replacementItem.clone();
        this.rootPath = builder.rootPath;
        this.displayPath = builder.displayPath;
        this.save = builder.save;
        this.force = builder.force;
        this.fileName = builder.fileName;
        this.caseSensitive = builder.caseSensitive;
        this.ignoreColors = builder.ignoreColors;
        this.keepColors = builder.keepColors;
    }

    public static Builder builder(SearchMode mode) {
        return new Builder(mode);
    }

    public SearchMode getMode() {
        return mode;
    }

    public String getTarget() {
        return target;
    }

    public String getReplacement() {
        return replacement;
    }

    public ItemStack getTargetItem() {
        return targetItem == null ? null : targetItem.clone();
    }

    public ItemStack getReplacementItem() {
        return replacementItem == null ? null : replacementItem.clone();
    }

    public Path getRootPath() {
        return rootPath;
    }

    public String getDisplayPath() {
        return displayPath;
    }

    public boolean isSave() {
        return save;
    }

    public boolean isForce() {
        return force;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * Регистрозависимость, заданная флагом команды.
     * {@code null} — использовать значение из конфигурации.
     */
    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public boolean isCaseSensitive(boolean fallback) {
        return caseSensitive == null ? fallback : caseSensitive.booleanValue();
    }

    public boolean isIgnoreColors() {
        return ignoreColors;
    }

    public boolean isKeepColors() {
        return keepColors;
    }

    public static final class Builder {
        private final SearchMode mode;
        private String target;
        private String replacement = "";
        private ItemStack targetItem;
        private ItemStack replacementItem;
        private Path rootPath;
        private String displayPath;
        private boolean save;
        private boolean force;
        private String fileName;
        private Boolean caseSensitive;
        private boolean ignoreColors;
        private boolean keepColors = true;

        private Builder(SearchMode mode) {
            this.mode = mode;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder replacement(String replacement) {
            this.replacement = replacement;
            return this;
        }

        public Builder targetItem(ItemStack targetItem) {
            this.targetItem = targetItem == null ? null : targetItem.clone();
            return this;
        }

        public Builder replacementItem(ItemStack replacementItem) {
            this.replacementItem = replacementItem == null ? null : replacementItem.clone();
            return this;
        }

        public Builder rootPath(Path rootPath) {
            this.rootPath = rootPath;
            return this;
        }

        public Builder displayPath(String displayPath) {
            this.displayPath = displayPath;
            return this;
        }

        public Builder save(boolean save) {
            this.save = save;
            return this;
        }

        public Builder force(boolean force) {
            this.force = force;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder caseSensitive(Boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder ignoreColors(boolean ignoreColors) {
            this.ignoreColors = ignoreColors;
            return this;
        }

        public Builder keepColors(boolean keepColors) {
            this.keepColors = keepColors;
            return this;
        }

        public SearchOptions build() {
            return new SearchOptions(this);
        }
    }
}
