package com.enumdev.enumdevelopment.models;

public final class SearchResult {

    private final String file;
    private final int line;
    private final int occurrences;
    private final String content;
    private final String before;
    private final String after;

    private SearchResult(String file, int line, int occurrences, String content, String before, String after) {
        this.file = file;
        this.line = line;
        this.occurrences = occurrences;
        this.content = content;
        this.before = before;
        this.after = after;
    }

    public static SearchResult found(String file, int line, int occurrences, String content) {
        return new SearchResult(file, line, occurrences, content, content, content);
    }

    public static SearchResult replaced(String file, int line, int occurrences, String before, String after) {
        return new SearchResult(file, line, occurrences, after, before, after);
    }

    public String getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public String getContent() {
        return content;
    }

    public String getBefore() {
        return before;
    }

    public String getAfter() {
        return after;
    }
}
