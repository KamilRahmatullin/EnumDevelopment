package com.enumdev.enumdevelopment.services.item;

import com.enumdev.enumdevelopment.config.ConfigManager;
import com.enumdev.enumdevelopment.managers.UndoManager;
import com.enumdev.enumdevelopment.models.SearchMode;
import com.enumdev.enumdevelopment.models.SearchOptions;
import com.enumdev.enumdevelopment.models.SearchReport;
import com.enumdev.enumdevelopment.models.SearchResult;
import com.enumdev.enumdevelopment.models.UndoSession;
import com.enumdev.enumdevelopment.utils.ItemDescriptionUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemSearchService {

    private final ConfigManager configManager;
    private final Base64ItemCodec base64ItemCodec = new Base64ItemCodec();

    public ItemSearchService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void processFile(Path path, SearchOptions options, SearchReport report, UndoSession undoSession, UndoManager undoManager) throws IOException {
        if (isYamlFile(path)) {
            try {
                processYamlFile(path, options, report, undoSession, undoManager);
                return;
            } catch (InvalidConfigurationException ignored) {
                processRawFile(path, options, report, undoSession, undoManager);
                return;
            }
        }
        processRawFile(path, options, report, undoSession, undoManager);
    }

    private void processYamlFile(Path path, SearchOptions options, SearchReport report, UndoSession undoSession, UndoManager undoManager) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());

        ItemMatcher itemMatcher = new ItemMatcher(configManager.isItemSearchMatchAmount());
        YamlScanContext context = new YamlScanContext(path, options, report, itemMatcher);
        scanSection(yaml, "", yaml, context);

        if (context.hasChanges()) {
            if (undoManager != null) {
                undoManager.backupBeforeChange(undoSession, path, options.getRootPath());
            }
            Path temp = path.resolveSibling(path.getFileName().toString() + ".enumdev-item-" + UUID.randomUUID().toString() + ".tmp");
            try {
                yaml.save(temp.toFile());
                moveReplacing(temp, path);
            } catch (IOException exception) {
                Files.deleteIfExists(temp);
                throw exception;
            }
        }

        report.addScannedFile();
        if (context.hasMatches()) {
            report.addMatchedFile();
        }
    }

    private void scanSection(ConfigurationSection root, String basePath, ConfigurationSection section, YamlScanContext context) throws IOException {
        for (String key : section.getKeys(false)) {
            String path = basePath.isEmpty() ? key : basePath + "." + key;
            Object value = section.get(key);
            YamlValueResult result = processYamlValue(root, path, value, context);
            if (result.isChanged()) {
                root.set(path, result.getValue());
                context.markChanged();
            }
        }
    }

    private YamlValueResult processYamlValue(ConfigurationSection root, String path, Object value, YamlScanContext context) throws IOException {
        if (value instanceof ItemStack) {
            return processYamlItem("yaml:" + path, path, (ItemStack) value, context);
        }

        if (value instanceof ConfigurationSection) {
            ConfigurationSection child = (ConfigurationSection) value;
            ItemStack item = tryReadItemStack(root, path, child);
            if (item != null) {
                return processYamlItem("yaml:" + path, path, item, context);
            }
            scanSection(root, path, child, context);
            return YamlValueResult.unchanged();
        }

        if (value instanceof List<?>) {
            List<Object> copy = new ArrayList<Object>((List<?>) value);
            if (scanYamlList(root, path, copy, context)) {
                return YamlValueResult.changed(copy);
            }
            return YamlValueResult.unchanged();
        }

        if (value instanceof Map<?, ?>) {
            return processYamlMap(root, path, (Map<?, ?>) value, context);
        }

        if (value instanceof String && configManager.isItemSearchScanBase64()) {
            String replaced = processBase64Value((String) value, context, "yaml-base64:" + path, context.findLine(path));
            if (replaced != null) {
                return YamlValueResult.changed(replaced);
            }
        }

        return YamlValueResult.unchanged();
    }

    private boolean scanYamlList(ConfigurationSection root, String path, List<Object> list, YamlScanContext context) throws IOException {
        boolean changed = false;
        for (int index = 0; index < list.size(); index++) {
            Object value = list.get(index);
            String entryPath = path + "[" + index + "]";
            YamlValueResult result = processYamlValue(root, entryPath, value, context);
            if (result.isChanged()) {
                list.set(index, result.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private YamlValueResult processYamlMap(ConfigurationSection root, String path, Map<?, ?> map, YamlScanContext context) throws IOException {
        ItemStack item = tryReadItemStack(map);
        if (item != null) {
            return processYamlItem("yaml:" + path, path, item, context);
        }

        Map<Object, Object> copy = null;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            String childPath = path + "." + String.valueOf(entry.getKey());
            YamlValueResult result = processYamlValue(root, childPath, value, context);
            if (!result.isChanged()) {
                continue;
            }
            if (copy == null) {
                copy = new LinkedHashMap<Object, Object>();
                for (Map.Entry<?, ?> original : map.entrySet()) {
                    copy.put(original.getKey(), original.getValue());
                }
            }
            copy.put(entry.getKey(), result.getValue());
        }

        if (copy == null) {
            return YamlValueResult.unchanged();
        }
        return YamlValueResult.changed(copy);
    }

    private YamlValueResult processYamlItem(String location, String linePath, ItemStack item, YamlScanContext context) {
        if (!context.matches(item)) {
            return YamlValueResult.unchanged();
        }

        if (context.isReplaceMode()) {
            ItemStack replacement = context.getReplacementItem(item);
            context.addReplacement(location, linePath, item, replacement);
            return YamlValueResult.changed(replacement);
        }

        context.addMatch(location, linePath, item);
        return YamlValueResult.unchanged();
    }

    private ItemStack tryReadItemStack(ConfigurationSection root, String path, ConfigurationSection section) {
        try {
            ItemStack direct = root.getItemStack(path);
            if (direct != null) {
                return direct;
            }
        } catch (Exception ignored) {
        }

        if (!looksLikeItemSection(section)) {
            return null;
        }

        try {
            return ItemStack.deserialize(toPlainMap(section));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemStack tryReadItemStack(Map<?, ?> map) {
        if (!looksLikeItemMap(map)) {
            return null;
        }

        Map<String, Object> normalized = normalizeSerializedMap(map);
        Object deserialized = deserializeSerializedMap(normalized);
        if (deserialized instanceof ItemStack) {
            return ((ItemStack) deserialized).clone();
        }

        try {
            return ItemStack.deserialize(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean looksLikeItemSection(ConfigurationSection section) {
        String marker = section.getString("==", "");
        if (marker.toLowerCase(Locale.ROOT).contains("itemstack")) {
            return true;
        }
        return section.isString("type") || section.isString("id");
    }

    private boolean looksLikeItemMap(Map<?, ?> map) {
        Object marker = map.get("==");
        if (marker != null && String.valueOf(marker).toLowerCase(Locale.ROOT).contains("itemstack")) {
            return true;
        }
        return map.containsKey("type") || map.containsKey("id");
    }

    private Map<String, Object> toPlainMap(ConfigurationSection section) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                map.put(key, toPlainMap((ConfigurationSection) value));
            } else if (value instanceof Map<?, ?>) {
                map.put(key, normalizeSerializedValue(value));
            } else if (value instanceof List<?>) {
                map.put(key, normalizeSerializedValue(value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private Map<String, Object> normalizeSerializedMap(Map<?, ?> source) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            map.put(String.valueOf(entry.getKey()), normalizeSerializedValue(entry.getValue()));
        }
        return map;
    }

    private Object normalizeSerializedValue(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> nested = normalizeSerializedMap((Map<?, ?>) value);
            Object deserialized = deserializeSerializedMap(nested);
            return deserialized == null ? nested : deserialized;
        }
        if (value instanceof List<?>) {
            List<Object> list = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                list.add(normalizeSerializedValue(item));
            }
            return list;
        }
        return value;
    }

    private Object deserializeSerializedMap(Map<String, Object> map) {
        if (!map.containsKey("==")) {
            return null;
        }
        try {
            return ConfigurationSerialization.deserializeObject(map);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void processRawFile(Path path, SearchOptions options, SearchReport report, UndoSession undoSession, UndoManager undoManager) throws IOException {
        Charset charset = configManager.getCharset();
        String content = new String(Files.readAllBytes(path), charset);
        ItemMatcher itemMatcher = new ItemMatcher(configManager.isItemSearchMatchAmount());
        RawScanContext context = new RawScanContext(path, options, report, itemMatcher, content);

        String updated = content;
        if (configManager.isItemSearchScanBase64()) {
            updated = replaceBase64Tokens(content, context);
        }

        if (context.hasChanges()) {
            if (undoManager != null) {
                undoManager.backupBeforeChange(undoSession, path, options.getRootPath());
            }
            Path temp = path.resolveSibling(path.getFileName().toString() + ".enumdev-item-" + UUID.randomUUID().toString() + ".tmp");
            try {
                Files.write(temp, updated.getBytes(charset));
                moveReplacing(temp, path);
            } catch (IOException exception) {
                Files.deleteIfExists(temp);
                throw exception;
            }
        }

        report.addScannedFile();
        if (context.hasMatches()) {
            report.addMatchedFile();
        }
    }

    private String replaceBase64Tokens(String content, RawScanContext context) throws IOException {
        Pattern pattern = Pattern.compile("[A-Za-z0-9+/=_-]{" + configManager.getItemSearchBase64MinLength() + ",}");
        Matcher matcher = pattern.matcher(content);
        StringBuffer buffer = null;
        Map<String, Base64ItemCodec.DecodedItems> cache = new HashMap<String, Base64ItemCodec.DecodedItems>();

        while (matcher.find()) {
            String token = matcher.group();
            Base64ItemCodec.DecodedItems decoded = cachedDecode(token, cache);
            if (decoded == null) {
                continue;
            }

            Base64ProcessResult result = evaluateDecoded(decoded, context);
            if (result.getMatches() <= 0) {
                continue;
            }

            int line = context.findLine(matcher.start());
            context.addBase64Match("base64", line, result.getFirstMatchedItem());
            if (!context.isReplaceMode()) {
                continue;
            }

            if (buffer == null) {
                buffer = new StringBuffer(content.length());
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(result.getReplacementValue()));
            context.markChanged();
        }

        if (buffer == null) {
            return content;
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String processBase64Value(String value, YamlScanContext context, String location, int line) throws IOException {
        Base64ItemCodec.DecodedItems decoded = base64ItemCodec.decode(value);
        if (decoded == null) {
            return null;
        }

        Base64ProcessResult result = evaluateDecoded(decoded, context);
        if (result.getMatches() <= 0) {
            return null;
        }

        context.addBase64Match(location, line, result.getFirstMatchedItem());
        return context.isReplaceMode() ? result.getReplacementValue() : null;
    }

    private Base64ItemCodec.DecodedItems cachedDecode(String token, Map<String, Base64ItemCodec.DecodedItems> cache) {
        if (cache.containsKey(token)) {
            return cache.get(token);
        }
        Base64ItemCodec.DecodedItems decoded = base64ItemCodec.decode(token);
        cache.put(token, decoded);
        return decoded;
    }

    private Base64ProcessResult evaluateDecoded(Base64ItemCodec.DecodedItems decoded, AbstractItemScanContext context) throws IOException {
        ItemStack[] items = decoded.getItems();
        ItemStack firstMatched = null;
        int matches = 0;
        boolean changed = false;

        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (!context.matches(item)) {
                continue;
            }
            matches++;
            if (firstMatched == null) {
                firstMatched = item == null ? null : item.clone();
            }
            if (context.isReplaceMode()) {
                items[index] = context.getReplacementItem(item);
                changed = true;
            }
        }

        if (matches <= 0) {
            return Base64ProcessResult.empty();
        }

        context.addMatches(matches);
        if (!context.isReplaceMode()) {
            return Base64ProcessResult.matched(matches, firstMatched, null);
        }

        context.addReplacements(matches);
        String replacementValue = changed ? base64ItemCodec.encode(decoded, items) : null;
        return Base64ProcessResult.matched(matches, firstMatched, replacementValue);
    }

    private boolean isYamlFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String relative(Path path, Path root) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    private abstract class AbstractItemScanContext {
        private final Path path;
        private final SearchOptions options;
        private final SearchReport report;
        private final ItemMatcher itemMatcher;
        private boolean matched;
        private boolean changed;

        private AbstractItemScanContext(Path path, SearchOptions options, SearchReport report, ItemMatcher itemMatcher) {
            this.path = path;
            this.options = options;
            this.report = report;
            this.itemMatcher = itemMatcher;
        }

        protected boolean matches(ItemStack item) {
            return itemMatcher.matches(options.getTargetItem(), item);
        }

        protected boolean isReplaceMode() {
            return options.getMode() == SearchMode.REPLACE_ITEM;
        }

        protected ItemStack getReplacementItem(ItemStack matchedItem) {
            ItemStack replacement = options.getReplacementItem();
            if (replacement == null) {
                return null;
            }
            if (configManager.isItemSearchPreserveAmountOnReplace() && matchedItem != null) {
                replacement.setAmount(matchedItem.getAmount());
            }
            return replacement;
        }

        protected void addMatches(long amount) {
            matched = true;
            report.addMatches(amount);
        }

        protected void addReplacements(long amount) {
            report.addReplacements(amount);
        }

        protected void markChanged() {
            changed = true;
        }

        protected boolean hasMatches() {
            return matched;
        }

        protected boolean hasChanges() {
            return changed;
        }

        protected String file() {
            return relative(path, options.getRootPath());
        }

        protected SearchReport report() {
            return report;
        }

        protected Path path() {
            return path;
        }
    }

    private final class YamlScanContext extends AbstractItemScanContext {
        private List<String> lines;

        private YamlScanContext(Path path, SearchOptions options, SearchReport report, ItemMatcher itemMatcher) {
            super(path, options, report, itemMatcher);
        }

        private void addMatch(String location, String configPath, ItemStack item) {
            addMatches(1L);
            int line = findLine(configPath);
            report().addResult(SearchResult.found(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(item)));
        }

        private void addReplacement(String location, String configPath, ItemStack before, ItemStack after) {
            addMatches(1L);
            addReplacements(1L);
            int line = findLine(configPath);
            report().addResult(SearchResult.replaced(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(before), location + " -> " + ItemDescriptionUtil.describe(after)));
        }

        private void addBase64Match(String location, int line, ItemStack item) {
            if (isReplaceMode()) {
                report().addResult(SearchResult.replaced(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(item), location + " -> " + ItemDescriptionUtil.describe(getReplacementItem(item))));
                return;
            }
            report().addResult(SearchResult.found(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(item)));
        }

        private int findLine(String configPath) {
            if (configPath == null || configPath.isEmpty()) {
                return 0;
            }

            ListIndexPath listIndexPath = parseLastListIndex(configPath);
            if (listIndexPath != null) {
                int parentLine = findKeyLine(listIndexPath.getParentPath());
                return findListEntryLine(parentLine, listIndexPath.getIndex());
            }
            return findKeyLine(configPath);
        }

        private int findKeyLine(String configPath) {
            String key = configPath;
            int dot = key.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < key.length()) {
                key = key.substring(dot + 1);
            }
            int bracket = key.indexOf('[');
            if (bracket >= 0) {
                key = key.substring(0, bracket);
            }

            List<String> content = lines();
            for (int index = 0; index < content.size(); index++) {
                String normalized = content.get(index).trim();
                if (normalized.startsWith(key + ":") || normalized.startsWith("'" + key + "':") || normalized.startsWith("\"" + key + "\":")) {
                    return index + 1;
                }
            }
            return 0;
        }

        private int findListEntryLine(int parentLine, int targetIndex) {
            if (parentLine <= 0) {
                return 0;
            }

            List<String> content = lines();
            int parentArrayIndex = parentLine - 1;
            if (parentArrayIndex < 0 || parentArrayIndex >= content.size()) {
                return parentLine;
            }

            int itemIndent = -1;
            int seen = -1;
            for (int index = parentArrayIndex + 1; index < content.size(); index++) {
                String line = content.get(index);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int indent = countIndent(line);
                if (itemIndent < 0) {
                    if (!trimmed.startsWith("-")) {
                        continue;
                    }
                    itemIndent = indent;
                }

                if (indent < itemIndent) {
                    break;
                }
                if (indent == itemIndent && trimmed.startsWith("-")) {
                    seen++;
                    if (seen == targetIndex) {
                        return index + 1;
                    }
                }
            }
            return parentLine;
        }

        private int countIndent(String line) {
            int count = 0;
            while (count < line.length() && line.charAt(count) == ' ') {
                count++;
            }
            return count;
        }

        private ListIndexPath parseLastListIndex(String configPath) {
            int close = configPath.lastIndexOf(']');
            int open = close < 0 ? -1 : configPath.lastIndexOf('[', close);
            if (open < 0 || close < 0 || open + 1 >= close) {
                return null;
            }
            try {
                int index = Integer.parseInt(configPath.substring(open + 1, close));
                return new ListIndexPath(configPath.substring(0, open), index);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private List<String> lines() {
            if (lines != null) {
                return lines;
            }
            try {
                lines = Files.readAllLines(path(), configManager.getCharset());
            } catch (IOException ignored) {
                lines = new ArrayList<String>();
            }
            return lines;
        }
    }

    private final class RawScanContext extends AbstractItemScanContext {
        private final int[] lineStarts;

        private RawScanContext(Path path, SearchOptions options, SearchReport report, ItemMatcher itemMatcher, String content) {
            super(path, options, report, itemMatcher);
            this.lineStarts = buildLineStarts(content);
        }

        private void addBase64Match(String location, int line, ItemStack item) {
            if (isReplaceMode()) {
                report().addResult(SearchResult.replaced(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(item), location + " -> " + ItemDescriptionUtil.describe(getReplacementItem(item))));
                return;
            }
            report().addResult(SearchResult.found(file(), line, 1, location + " -> " + ItemDescriptionUtil.describe(item)));
        }

        private int findLine(int offset) {
            int low = 0;
            int high = lineStarts.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (lineStarts[middle] <= offset) {
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            return Math.max(1, high + 1);
        }

        private int[] buildLineStarts(String content) {
            List<Integer> starts = new ArrayList<Integer>();
            starts.add(0);
            for (int index = 0; index < content.length(); index++) {
                if (content.charAt(index) == '\n') {
                    starts.add(index + 1);
                }
            }
            int[] result = new int[starts.size()];
            for (int index = 0; index < starts.size(); index++) {
                result[index] = starts.get(index);
            }
            return result;
        }
    }

    private static final class YamlValueResult {
        private final boolean changed;
        private final Object value;

        private YamlValueResult(boolean changed, Object value) {
            this.changed = changed;
            this.value = value;
        }

        private static YamlValueResult unchanged() {
            return new YamlValueResult(false, null);
        }

        private static YamlValueResult changed(Object value) {
            return new YamlValueResult(true, value);
        }

        private boolean isChanged() {
            return changed;
        }

        private Object getValue() {
            return value;
        }
    }

    private static final class ListIndexPath {
        private final String parentPath;
        private final int index;

        private ListIndexPath(String parentPath, int index) {
            this.parentPath = parentPath;
            this.index = index;
        }

        private String getParentPath() {
            return parentPath;
        }

        private int getIndex() {
            return index;
        }
    }

    private static final class Base64ProcessResult {
        private final int matches;
        private final ItemStack firstMatchedItem;
        private final String replacementValue;

        private Base64ProcessResult(int matches, ItemStack firstMatchedItem, String replacementValue) {
            this.matches = matches;
            this.firstMatchedItem = firstMatchedItem == null ? null : firstMatchedItem.clone();
            this.replacementValue = replacementValue;
        }

        private static Base64ProcessResult empty() {
            return new Base64ProcessResult(0, null, null);
        }

        private static Base64ProcessResult matched(int matches, ItemStack firstMatchedItem, String replacementValue) {
            return new Base64ProcessResult(matches, firstMatchedItem, replacementValue);
        }

        private int getMatches() {
            return matches;
        }

        private ItemStack getFirstMatchedItem() {
            return firstMatchedItem == null ? null : firstMatchedItem.clone();
        }

        private String getReplacementValue() {
            return replacementValue;
        }
    }
}
