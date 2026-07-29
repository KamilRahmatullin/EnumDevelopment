package com.enumdev.enumdevelopment.models;

public enum SearchMode {
    FIND,
    REPLACE,
    FIND_ITEM,
    REPLACE_ITEM;

    public boolean isFindMode() {
        return this == FIND || this == FIND_ITEM;
    }

    public boolean isReplaceMode() {
        return this == REPLACE || this == REPLACE_ITEM;
    }

    public boolean isItemMode() {
        return this == FIND_ITEM || this == REPLACE_ITEM;
    }
}
