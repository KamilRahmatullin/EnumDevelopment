package com.enumdev.enumdevelopment.services;

import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.managers.UndoManager;
import com.enumdev.enumdevelopment.models.SearchMode;
import com.enumdev.enumdevelopment.models.SearchOptions;
import com.enumdev.enumdevelopment.models.SearchReport;
import com.enumdev.enumdevelopment.models.SearchResult;
import com.enumdev.enumdevelopment.models.UndoSession;
import com.enumdev.enumdevelopment.services.item.ItemSearchService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileSearchService {

    private final ConfigManager configManager;
    private final ItemSearchService itemSearchService;

    public FileSearchService(ConfigManager configManager) {
        this.configManager = configManager;
        this.itemSearchService = new ItemSearchService(configManager);
    }

    public SearchReport execute(SearchOptions options, SearchProgressListener progressListener, UndoSession undoSession, UndoManager undoManager) {
        SearchReport report = new SearchReport(
                options.getMode(),
                options.getRootPath(),
                options.getTarget(),
                options.getReplacement(),
                configManager.getMaxStoredResults()
        );

        Set<FileVisitOption> visitOptions = configManager.isFollowSymbolicLinks()
                ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                : EnumSet.noneOf(FileVisitOption.class);

        try {
            Files.walkFileTree(options.getRootPath(), visitOptions, configManager.getMaxDepth(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (shouldCancel(progressListener)) {
                        throw new SearchCancelledException();
                    }
                    if (!directory.equals(options.getRootPath()) && isIgnoredDirectory(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (shouldCancel(progressListener)) {
                        throw new SearchCancelledException();
                    }
                    if (attributes == null || !attributes.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }

                    processFile(file, options, report, undoSession, undoManager);
                    sendProgress(progressListener, report, file, options.getRootPath());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    report.addError(file + ": " + (exception == null ? "unreadable" : exception.getMessage()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (SearchCancelledException exception) {
            throw exception;
        } catch (Exception exception) {
            report.addError(options.getRootPath() + ": " + exception.getMessage());
        } finally {
            report.finish();
        }

        return report;
    }

    private boolean shouldCancel(SearchProgressListener progressListener) {
        return progressListener != null && progressListener.isCancelled();
    }

    private void sendProgress(SearchProgressListener progressListener, SearchReport report, Path currentFile, Path root) {
        if (progressListener == null) {
            return;
        }
        progressListener.onProgress(report.snapshot(relative(currentFile, root)));
    }

    private boolean isIgnoredDirectory(Path directory) {
        List<String> ignoredDirectories = configManager.getIgnoredDirectories();
        if (ignoredDirectories.isEmpty() || directory.getFileName() == null) {
            return false;
        }
        return ignoredDirectories.contains(directory.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    private void processFile(Path path, SearchOptions options, SearchReport report, UndoSession undoSession, UndoManager undoManager) {
        report.addTotalFile();

        try {
            if (!isFileAllowed(path)) {
                report.addSkippedFile();
                return;
            }

            if (options.getMode().isItemMode()) {
                itemSearchService.processFile(path, options, report, undoSession, undoManager);
                return;
            }
            if (options.getMode() == SearchMode.FIND) {
                findInFile(path, options, report);
                return;
            }
            replaceInFile(path, options, report, undoSession, undoManager);
        } catch (Exception exception) {
            report.addError(path + ": " + exception.getMessage());
        }
    }

    private boolean isFileAllowed(Path path) throws IOException {
        if (Files.size(path) > configManager.getMaxFileSizeBytes()) {
            return false;
        }

        List<String> allowedExtensions = configManager.getAllowedExtensions();
        if (!allowedExtensions.isEmpty()) {
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean matches = false;
            for (String extension : allowedExtensions) {
                if (fileName.endsWith(extension)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }

        return configManager.isSearchBinaryFiles() || !looksBinary(path);
    }

    private boolean looksBinary(Path path) throws IOException {
        byte[] buffer = new byte[2048];
        int read;
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            read = input.read(buffer);
        }
        if (read <= 0) {
            return false;
        }
        for (int index = 0; index < read; index++) {
            if (buffer[index] == 0) {
                return true;
            }
        }
        return false;
    }

    private void findInFile(Path path, SearchOptions options, SearchReport report) throws IOException {
        Charset charset = configManager.getCharset();
        boolean matchedFile = false;
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                int occurrences = countOccurrences(line, options.getTarget(), configManager.isCaseSensitive());
                if (occurrences <= 0) {
                    continue;
                }
                matchedFile = true;
                report.addMatches(occurrences);
                report.addResult(SearchResult.found(relative(path, options.getRootPath()), lineNumber, occurrences, line));
            }
        }

        report.addScannedFile();
        if (matchedFile) {
            report.addMatchedFile();
        }
    }

    private void replaceInFile(Path path, SearchOptions options, SearchReport report, UndoSession undoSession, UndoManager undoManager) throws IOException {
        Charset charset = configManager.getCharset();
        Path temp = path.resolveSibling(path.getFileName().toString() + ".enumdev-" + UUID.randomUUID() + ".tmp");
        boolean matchedFile = false;
        int lineNumber = 0;
        long replacementsInFile = 0;

        try (BufferedReader reader = Files.newBufferedReader(path, charset);
             BufferedWriter writer = Files.newBufferedWriter(temp, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                ReplaceLineResult replaced = replaceLine(line, options.getTarget(), options.getReplacement(), configManager.isCaseSensitive());
                if (replaced.getOccurrences() > 0) {
                    matchedFile = true;
                    replacementsInFile += replaced.getOccurrences();
                    report.addMatches(replaced.getOccurrences());
                    report.addReplacements(replaced.getOccurrences());
                    report.addResult(SearchResult.replaced(relative(path, options.getRootPath()), lineNumber, replaced.getOccurrences(), line, replaced.getLine()));
                }
                writer.write(replaced.getLine());
                writer.newLine();
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }

        if (matchedFile && replacementsInFile > 0) {
            try {
                if (undoManager != null) {
                    undoManager.backupBeforeChange(undoSession, path, options.getRootPath());
                }
                moveReplacing(temp, path);
            } catch (IOException exception) {
                Files.deleteIfExists(temp);
                throw exception;
            }
        } else {
            Files.deleteIfExists(temp);
        }

        report.addScannedFile();
        if (matchedFile) {
            report.addMatchedFile();
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private int countOccurrences(String line, String target, boolean caseSensitive) {
        if (target == null || target.isEmpty() || line.isEmpty()) {
            return 0;
        }

        String haystack = caseSensitive ? line : line.toLowerCase(Locale.ROOT);
        String needle = caseSensitive ? target : target.toLowerCase(Locale.ROOT);
        int count = 0;
        int index = 0;

        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private ReplaceLineResult replaceLine(String line, String target, String replacement, boolean caseSensitive) {
        if (target == null || target.isEmpty()) {
            return new ReplaceLineResult(line, 0);
        }

        if (caseSensitive) {
            int occurrences = countOccurrences(line, target, true);
            if (occurrences == 0) {
                return new ReplaceLineResult(line, 0);
            }
            return new ReplaceLineResult(line.replace(target, replacement), occurrences);
        }

        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(line);
        StringBuffer buffer = new StringBuffer();
        int occurrences = 0;
        while (matcher.find()) {
            occurrences++;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return new ReplaceLineResult(buffer.toString(), occurrences);
    }

    private String relative(Path path, Path root) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    private static final class ReplaceLineResult {
        private final String line;
        private final int occurrences;

        private ReplaceLineResult(String line, int occurrences) {
            this.line = line;
            this.occurrences = occurrences;
        }

        private String getLine() {
            return line;
        }

        private int getOccurrences() {
            return occurrences;
        }
    }
}
