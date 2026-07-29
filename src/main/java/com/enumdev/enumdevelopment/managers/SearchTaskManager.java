package com.enumdev.enumdevelopment.managers;

import com.enumdev.enumdevelopment.Main;
import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.models.CommandTarget;
import com.enumdev.enumdevelopment.models.SearchMode;
import com.enumdev.enumdevelopment.models.SearchOptions;
import com.enumdev.enumdevelopment.models.SearchProgressSnapshot;
import com.enumdev.enumdevelopment.models.SearchReport;
import com.enumdev.enumdevelopment.models.SearchResult;
import com.enumdev.enumdevelopment.models.SearchTaskResult;
import com.enumdev.enumdevelopment.models.UndoRestoreResult;
import com.enumdev.enumdevelopment.models.UndoSession;
import com.enumdev.enumdevelopment.services.FileSearchService;
import com.enumdev.enumdevelopment.services.SearchCancelledException;
import com.enumdev.enumdevelopment.services.SearchProgressListener;
import com.enumdev.enumdevelopment.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SearchTaskManager {

    private final Main plugin;
    private final ConfigManager configManager;
    private final MessageUtil messageUtil;
    private final FileSearchService fileSearchService;
    private final ResultStorageManager resultStorageManager;
    private final UndoManager undoManager;
    private final Map<Integer, BukkitTask> activeTasks = new ConcurrentHashMap<Integer, BukkitTask>();

    public SearchTaskManager(Main plugin, ConfigManager configManager, MessageUtil messageUtil, UndoManager undoManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messageUtil = messageUtil;
        this.undoManager = undoManager;
        this.fileSearchService = new FileSearchService(configManager);
        this.resultStorageManager = new ResultStorageManager(plugin, configManager);
    }

    public void startTask(CommandSender sender, SearchOptions options) {
        cleanupFinishedTasks();
        if (activeTasks.size() >= configManager.getMaxActiveTasks()) {
            messageUtil.send(sender, "task-limit");
            return;
        }

        CommandTarget target = CommandTarget.from(sender);
        sendStartedMessage(sender, options);

        BukkitRunnable runnable = new BukkitRunnable() {
            private final AtomicLong lastProgressAt = new AtomicLong(System.currentTimeMillis());
            private final AtomicLong lastProgressFiles = new AtomicLong(0L);

            @Override
            public void run() {
                int taskId = getTaskId();
                UndoSession undoSession = null;
                boolean undoAvailable = false;
                try {
                    if (options.getMode().isReplaceMode()) {
                        undoSession = undoManager.beginSession(options);
                    }

                    SearchReport report = fileSearchService.execute(options, createProgressListener(target, this, lastProgressAt, lastProgressFiles), undoSession, undoManager);
                    undoAvailable = completeUndoSession(undoSession);
                    Path savedFile = null;
                    if (options.isSave()) {
                        savedFile = resultStorageManager.save(report, options.getFileName(), options.isForce());
                    }
                    deliverSuccess(target, new SearchTaskResult(report, savedFile, undoAvailable));
                } catch (SearchCancelledException exception) {
                    undoAvailable = completeUndoSession(undoSession);
                    deliverCancelled(target);
                    if (undoAvailable) {
                        deliverUndoAvailable(target);
                    }
                } catch (ResultStorageManager.FileAlreadyExistsResultException exception) {
                    deliverFileExists(target, exception.getFile());
                    if (undoAvailable) {
                        deliverUndoAvailable(target);
                    }
                } catch (Exception exception) {
                    undoAvailable = completeUndoSession(undoSession);
                    plugin.getLogger().warning("Task failed: " + exception.getMessage());
                    deliverFailure(target, exception.getMessage());
                    if (undoAvailable) {
                        deliverUndoAvailable(target);
                    }
                } finally {
                    activeTasks.remove(taskId);
                }
            }
        };

        BukkitTask task = runnable.runTaskAsynchronously(plugin);
        activeTasks.put(task.getTaskId(), task);
    }

    public void startUndoTask(CommandSender sender) {
        cleanupFinishedTasks();
        if (activeTasks.size() >= configManager.getMaxActiveTasks()) {
            messageUtil.send(sender, "task-limit");
            return;
        }

        CommandTarget target = CommandTarget.from(sender);
        messageUtil.send(sender, "task-started-undo");

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                int taskId = getTaskId();
                try {
                    UndoRestoreResult result = undoManager.restoreLatest();
                    deliverUndoResult(target, result);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Undo task failed: " + exception.getMessage());
                    deliverFailure(target, exception.getMessage());
                } finally {
                    activeTasks.remove(taskId);
                }
            }
        };

        BukkitTask task = runnable.runTaskAsynchronously(plugin);
        activeTasks.put(task.getTaskId(), task);
    }

    private boolean completeUndoSession(UndoSession undoSession) {
        try {
            if (undoSession == null || !undoSession.hasEntries()) {
                undoManager.discardSession(undoSession);
                return false;
            }
            undoManager.completeSession(undoSession);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to complete undo session: " + exception.getMessage());
            return false;
        }
    }

    private SearchProgressListener createProgressListener(CommandTarget target, BukkitRunnable runnable,
                                                          AtomicLong lastProgressAt, AtomicLong lastProgressFiles) {
        return new SearchProgressListener() {
            @Override
            public boolean isCancelled() {
                return runnable.isCancelled() || !plugin.isEnabled();
            }

            @Override
            public void onProgress(SearchProgressSnapshot snapshot) {
                if (!configManager.isProgressEnabled() || snapshot.getTotalFiles() <= 0) {
                    return;
                }

                long now = System.currentTimeMillis();
                long fileDelta = snapshot.getTotalFiles() - lastProgressFiles.get();
                long timeDelta = now - lastProgressAt.get();
                if (fileDelta < configManager.getProgressIntervalFiles() && timeDelta < configManager.getProgressIntervalMillis()) {
                    return;
                }

                lastProgressFiles.set(snapshot.getTotalFiles());
                lastProgressAt.set(now);
                deliverProgress(target, snapshot);
            }
        };
    }

    private void sendStartedMessage(CommandSender sender, SearchOptions options) {
        if (options.getMode() == SearchMode.FIND) {
            messageUtil.send(sender, "task-started-find", "target", options.getTarget(), "path", options.getDisplayPath());
            return;
        }
        if (options.getMode() == SearchMode.REPLACE) {
            messageUtil.send(sender, "task-started-replace", "target", options.getTarget(), "replacement", options.getReplacement(), "path", options.getDisplayPath());
            return;
        }
        if (options.getMode() == SearchMode.FIND_ITEM) {
            messageUtil.send(sender, "task-started-finditem", "target", options.getTarget(), "path", options.getDisplayPath());
            return;
        }
        messageUtil.send(sender, "task-started-replaceitem", "target", options.getTarget(), "replacement", options.getReplacement(), "path", options.getDisplayPath());
    }

    private void deliverSuccess(CommandTarget target, SearchTaskResult taskResult) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender == null) {
                    return;
                }

                SearchReport report = taskResult.getReport();
                sendSummary(sender, report);
                sendResults(sender, report);

                if (report.isTruncated()) {
                    messageUtil.send(sender, "results-truncated", "stored", String.valueOf(report.getResults().size()), "total", String.valueOf(report.getTotalMatches()));
                }
                if (!report.getErrors().isEmpty()) {
                    messageUtil.send(sender, "errors-detected", "errors", String.valueOf(report.getErrors().size()));
                }
                if (taskResult.getSavedFile() != null) {
                    messageUtil.send(sender, "results-saved", "file", taskResult.getSavedFile().toString());
                }
                if (taskResult.isUndoAvailable()) {
                    messageUtil.send(sender, "undo-available");
                }
            }
        });
    }

    private void sendSummary(CommandSender sender, SearchReport report) {
        if (report.getMode().isFindMode()) {
            messageUtil.send(sender, "search-finished",
                    "time", String.valueOf(report.getDurationMillis()),
                    "total", String.valueOf(report.getTotalFiles()),
                    "files", String.valueOf(report.getScannedFiles()),
                    "skipped", String.valueOf(report.getSkippedFiles()),
                    "matched", String.valueOf(report.getMatchedFiles()),
                    "matches", String.valueOf(report.getTotalMatches()));
            return;
        }
        messageUtil.send(sender, "replace-finished",
                "time", String.valueOf(report.getDurationMillis()),
                "total", String.valueOf(report.getTotalFiles()),
                "files", String.valueOf(report.getScannedFiles()),
                "skipped", String.valueOf(report.getSkippedFiles()),
                "matched", String.valueOf(report.getMatchedFiles()),
                "replacements", String.valueOf(report.getTotalReplacements()));
    }

    private void sendResults(CommandSender sender, SearchReport report) {
        if (report.getResults().isEmpty()) {
            messageUtil.send(sender, "no-results");
            return;
        }

        int limit = Math.min(configManager.getMaxChatResults(), report.getResults().size());
        for (int index = 0; index < limit; index++) {
            SearchResult result = report.getResults().get(index);
            Map<String, String> placeholders = new HashMap<String, String>();
            placeholders.put("file", result.getFile());
            placeholders.put("line", String.valueOf(result.getLine()));
            placeholders.put("occurrences", String.valueOf(result.getOccurrences()));
            placeholders.put("content", trim(result.getContent()));
            placeholders.put("before", trim(result.getBefore()));
            placeholders.put("after", trim(result.getAfter()));
            messageUtil.sendRaw(sender, report.getMode().isFindMode() ? "result-line-find" : "result-line-replace", placeholders);
        }
    }

    private void deliverProgress(CommandTarget target, SearchProgressSnapshot snapshot) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender == null) {
                    return;
                }

                messageUtil.send(sender, "task-progress",
                        "time", String.valueOf(snapshot.getElapsedMillis()),
                        "total", String.valueOf(snapshot.getTotalFiles()),
                        "scanned", String.valueOf(snapshot.getScannedFiles()),
                        "skipped", String.valueOf(snapshot.getSkippedFiles()),
                        "matched", String.valueOf(snapshot.getMatchedFiles()),
                        "matches", String.valueOf(snapshot.getTotalMatches()),
                        "replacements", String.valueOf(snapshot.getTotalReplacements()),
                        "file", trim(snapshot.getCurrentFile()));
            }
        });
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > 160 ? normalized.substring(0, 157) + "..." : normalized;
    }

    private void deliverUndoAvailable(CommandTarget target) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender != null) {
                    messageUtil.send(sender, "undo-available");
                }
            }
        });
    }

    private void deliverUndoResult(CommandTarget target, UndoRestoreResult result) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender == null) {
                    return;
                }

                if (!result.isSessionFound()) {
                    messageUtil.send(sender, "undo-none");
                    return;
                }

                if (result.getErrors().isEmpty()) {
                    messageUtil.send(sender, "undo-finished",
                            "time", String.valueOf(result.getDurationMillis()),
                            "restored", String.valueOf(result.getRestoredFiles()),
                            "total", String.valueOf(result.getTotalFiles()));
                    return;
                }

                messageUtil.send(sender, "undo-partial",
                        "time", String.valueOf(result.getDurationMillis()),
                        "restored", String.valueOf(result.getRestoredFiles()),
                        "total", String.valueOf(result.getTotalFiles()),
                        "errors", String.valueOf(result.getErrors().size()));
            }
        });
    }

    private void deliverFileExists(CommandTarget target, Path file) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender != null) {
                    messageUtil.send(sender, "file-exists", "file", file.toString());
                }
            }
        });
    }

    private void deliverCancelled(CommandTarget target) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender != null) {
                    messageUtil.send(sender, "task-cancelled");
                }
            }
        });
    }

    private void deliverFailure(CommandTarget target, String error) {
        runSyncIfEnabled(new Runnable() {
            @Override
            public void run() {
                CommandSender sender = target.resolve();
                if (sender != null) {
                    messageUtil.send(sender, "task-failed", "error", error == null ? "unknown" : error);
                }
            }
        });
    }

    private void runSyncIfEnabled(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private void cleanupFinishedTasks() {
        for (Map.Entry<Integer, BukkitTask> entry : activeTasks.entrySet()) {
            int taskId = entry.getKey();
            if (!Bukkit.getScheduler().isQueued(taskId) && !Bukkit.getScheduler().isCurrentlyRunning(taskId)) {
                activeTasks.remove(taskId);
            }
        }
    }

    public void cancelAll() {
        for (BukkitTask task : activeTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeTasks.clear();
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
