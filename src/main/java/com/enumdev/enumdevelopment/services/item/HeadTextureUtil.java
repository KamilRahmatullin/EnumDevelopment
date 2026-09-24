package com.enumdev.enumdevelopment.services.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads custom-head textures without depending on a specific CraftBukkit/Paper
 * implementation. The same head can be serialized with a different profile UUID
 * or owner name, so matching is based on the texture payload itself.
 */
public final class HeadTextureUtil {

    private static final Pattern TEXTURE_URL = Pattern.compile("(?i)(?:https?://)?textures\\.minecraft\\.net/texture/([0-9a-z_-]{16,})");
    private static final Pattern SNBT_TEXTURE_VALUE = Pattern.compile("(?i)(?:name\\s*[:=]\\s*[\\\"']textures[\\\"']?[^}]{0,256}?value\\s*[:=]\\s*[\\\"'])([A-Za-z0-9+/_=-]{20,})");
    private static final Pattern JSON_TEXTURE_VALUE = Pattern.compile("(?i)[\\\"']value[\\\"']\\s*:\\s*[\\\"']([A-Za-z0-9+/_=-]{20,})[\\\"']");
    private static final Pattern RAW_HASH = Pattern.compile("(?i)^[0-9a-f]{32,64}$");

    public TextureData extract(ItemStack item) {
        if (!isPlayerHead(item)) {
            return TextureData.empty();
        }

        TextureData reflected = extractFromMeta(item);
        if (reflected.hasTexture()) {
            return reflected;
        }

        try {
            return extractFromObject(item.serialize(), "item");
        } catch (Exception ignored) {
            return TextureData.empty();
        }
    }

    public TextureData extractFromObject(Object value, String path) {
        TextureAccumulator accumulator = new TextureAccumulator();
        scan(value, path == null ? "" : path.toLowerCase(Locale.ROOT), accumulator);
        return accumulator.toData();
    }

    public String normalizeTexture(String value) {
        if (value == null) {
            return null;
        }
        String candidate = stripQuotes(value.trim());
        if (candidate.isEmpty()) {
            return null;
        }

        Matcher urlMatcher = TEXTURE_URL.matcher(candidate);
        if (urlMatcher.find()) {
            return "hash:" + urlMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        if (RAW_HASH.matcher(candidate).matches()) {
            return "hash:" + candidate.toLowerCase(Locale.ROOT);
        }

        String decoded = decodeTexturePayload(candidate);
        if (decoded != null) {
            Matcher decodedUrl = TEXTURE_URL.matcher(decoded);
            if (decodedUrl.find()) {
                return "hash:" + decodedUrl.group(1).toLowerCase(Locale.ROOT);
            }
            return "base64:" + withoutPadding(candidate);
        }
        return null;
    }

    public String textureHash(String value) {
        String normalized = normalizeTexture(value);
        if (normalized != null && normalized.startsWith("hash:")) {
            return normalized.substring("hash:".length());
        }
        return null;
    }

    public UUID deterministicProfileId(String texture) {
        String normalized = normalizeTexture(texture);
        String source = normalized == null ? String.valueOf(texture) : normalized;
        return UUID.nameUUIDFromBytes(("EnumDevelopment:" + source).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isPlayerHead(ItemStack item) {
        if (item == null || item.getType() == null) {
            return false;
        }
        String name = item.getType().name();
        return "PLAYER_HEAD".equals(name) || "SKULL_ITEM".equals(name);
    }

    /**
     * Removes only profile identity/texture data and leaves display name, lore,
     * enchants, PDC and all other metadata intact for semantic comparison.
     */
    public ItemStack withoutProfile(ItemStack source) {
        if (!isPlayerHead(source)) {
            return source == null ? null : source.clone();
        }
        ItemStack copy = source.clone();
        try {
            ItemMeta meta = copy.getItemMeta();
            if (meta == null) {
                return copy;
            }

            if (meta instanceof SkullMeta) {
                SkullMeta skull = (SkullMeta) meta;
                try {
                    skull.setOwningPlayer(null);
                } catch (Throwable ignored) {
                }
                try {
                    skull.setOwner(null);
                } catch (Throwable ignored) {
                }
                clearProfileFields(skull);
                invokeNullableProfileSetters(skull);
                copy.setItemMeta(skull);
            }
        } catch (Throwable ignored) {
        }
        return copy;
    }

    private TextureData extractFromMeta(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof SkullMeta)) {
                return TextureData.empty();
            }
            TextureAccumulator accumulator = new TextureAccumulator();
            Object profile = invokeNoArg(meta, "getPlayerProfile");
            if (profile == null) {
                profile = invokeNoArg(meta, "getOwnerProfile");
            }
            if (profile == null) {
                profile = findFieldValue(meta, "profile");
            }
            if (profile != null) {
                readProfile(profile, accumulator);
            }
            if (accumulator.texture == null) {
                Object serialized = invokeNoArg(meta, "serialize");
                scan(serialized, "meta", accumulator);
            }
            return accumulator.toData();
        } catch (Throwable ignored) {
            return TextureData.empty();
        }
    }

    private void readProfile(Object profile, TextureAccumulator accumulator) {
        Object id = invokeNoArg(profile, "getUniqueId");
        if (id == null) {
            id = invokeNoArg(profile, "getId");
        }
        if (id instanceof UUID) {
            accumulator.profileId = (UUID) id;
        } else if (id != null) {
            try {
                accumulator.profileId = UUID.fromString(String.valueOf(id));
            } catch (IllegalArgumentException ignored) {
            }
        }

        Object name = invokeNoArg(profile, "getName");
        if (name != null) {
            accumulator.profileName = String.valueOf(name);
        }

        Object properties = invokeNoArg(profile, "getProperties");
        readProperties(properties, accumulator);
    }

    private void readProperties(Object properties, TextureAccumulator accumulator) {
        if (properties == null) {
            return;
        }
        if (properties instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) properties;
            Object textures = map.get("textures");
            if (textures == null) {
                textures = map.get("Textures");
            }
            readPropertyCollection(textures, accumulator);
            if (accumulator.texture == null) {
                scan(map, "profile.properties", accumulator);
            }
            return;
        }
        if (properties instanceof Iterable<?>) {
            readPropertyCollection(properties, accumulator);
            return;
        }
        scan(properties, "profile.properties", accumulator);
    }

    private void readPropertyCollection(Object collection, TextureAccumulator accumulator) {
        if (collection == null) {
            return;
        }
        if (collection instanceof Iterable<?>) {
            for (Object property : (Iterable<?>) collection) {
                readProperty(property, accumulator);
                if (accumulator.texture != null) {
                    return;
                }
            }
            return;
        }
        if (collection.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(collection);
            for (int index = 0; index < length; index++) {
                readProperty(java.lang.reflect.Array.get(collection, index), accumulator);
                if (accumulator.texture != null) {
                    return;
                }
            }
            return;
        }
        readProperty(collection, accumulator);
    }

    private void readProperty(Object property, TextureAccumulator accumulator) {
        if (property == null) {
            return;
        }
        Object name = invokeNoArg(property, "getName");
        if (name != null && !"textures".equalsIgnoreCase(String.valueOf(name))) {
            return;
        }
        Object value = invokeNoArg(property, "getValue");
        if (value == null) {
            value = findFieldValue(property, "value");
        }
        Object signature = invokeNoArg(property, "getSignature");
        if (signature == null) {
            signature = findFieldValue(property, "signature");
        }
        String normalized = value == null ? null : normalizeTexture(String.valueOf(value));
        if (normalized != null) {
            accumulator.texture = String.valueOf(value);
            accumulator.normalizedTexture = normalized;
            accumulator.signature = signature == null ? null : String.valueOf(signature);
        }
    }

    private void scan(Object value, String path, TextureAccumulator accumulator) {
        if (value == null || accumulator.texture != null) {
            return;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            readProfileMetadata(map, accumulator);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path + "." + key.toLowerCase(Locale.ROOT);
                Object child = entry.getValue();
                if (isTextureCandidateKey(key, path) && child instanceof String) {
                    acceptTexture(String.valueOf(child), accumulator);
                }
                if (accumulator.texture == null) {
                    scan(child, childPath, accumulator);
                }
            }
            return;
        }
        if (value instanceof Iterable<?>) {
            for (Object child : (Iterable<?>) value) {
                scan(child, path + "[]", accumulator);
                if (accumulator.texture != null) {
                    return;
                }
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                scan(java.lang.reflect.Array.get(value, index), path + "[]", accumulator);
                if (accumulator.texture != null) {
                    return;
                }
            }
            return;
        }
        if (value instanceof String && isProfilePath(path)) {
            String text = String.valueOf(value);
            acceptTexture(text, accumulator);
        }
    }

    private void readProfileMetadata(Map<?, ?> map, TextureAccumulator accumulator) {
        Object id = first(map, "profile-id", "profile_id", "id");
        if (id instanceof UUID) {
            accumulator.profileId = (UUID) id;
        } else if (id != null && accumulator.profileId == null) {
            try {
                accumulator.profileId = UUID.fromString(String.valueOf(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Object name = first(map, "profile-name", "profile_name", "name");
        if (name != null && accumulator.profileName == null && isProfileLikeMap(map)) {
            accumulator.profileName = String.valueOf(name);
        }
        Object signature = first(map, "signature", "sig");
        if (signature != null && accumulator.signature == null && isProfileLikeMap(map)) {
            accumulator.signature = String.valueOf(signature);
        }
    }

    private Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private boolean isProfileLikeMap(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            String normalized = String.valueOf(key).toLowerCase(Locale.ROOT);
            if (normalized.contains("profile") || normalized.contains("texture") || normalized.equals("properties")) {
                return true;
            }
        }
        return false;
    }

    private boolean isTextureCandidateKey(String key, String parentPath) {
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.equals("texture") || normalized.equals("textures") || normalized.equals("texture-value") || normalized.equals("texture_value")) {
            return true;
        }
        return normalized.equals("value") && isProfilePath(parentPath);
    }

    private boolean isProfilePath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return normalized.contains("profile") || normalized.contains("texture") || normalized.contains("skull") || normalized.contains("owner") || normalized.contains("properties");
    }

    private void acceptTexture(String value, TextureAccumulator accumulator) {
        String normalized = normalizeTexture(value);
        if (normalized != null) {
            accumulator.texture = stripQuotes(value.trim());
            accumulator.normalizedTexture = normalized;
        } else {
            acceptEmbeddedTexture(value, accumulator);
        }
    }

    private void acceptEmbeddedTexture(String text, TextureAccumulator accumulator) {
        Matcher url = TEXTURE_URL.matcher(text);
        if (url.find()) {
            String hash = url.group(1);
            accumulator.texture = createTexturePayload(hash);
            accumulator.normalizedTexture = "hash:" + hash.toLowerCase(Locale.ROOT);
            return;
        }
        Matcher snbt = SNBT_TEXTURE_VALUE.matcher(text);
        if (snbt.find()) {
            acceptTexture(snbt.group(1), accumulator);
            return;
        }
        Matcher json = JSON_TEXTURE_VALUE.matcher(text);
        while (json.find()) {
            String candidate = json.group(1);
            if (normalizeTexture(candidate) != null) {
                acceptTexture(candidate, accumulator);
                return;
            }
        }
    }

    public String createTexturePayload(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeTexturePayload(String value) {
        try {
            String padded = pad(value.replace('-', '+').replace('_', '/'));
            byte[] decoded = Base64.getDecoder().decode(padded);
            String text = new String(decoded, StandardCharsets.UTF_8);
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("textures") && lower.contains("skin") && lower.contains("url")) {
                return text;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private String pad(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value);
        for (int index = remainder; index < 4; index++) {
            builder.append('=');
        }
        return builder.toString();
    }

    private String withoutPadding(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '=') {
            end--;
        }
        return value.substring(0, end).replace('-', '+').replace('_', '/');
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object findFieldValue(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private void clearProfileFields(Object target) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.equals("profile") && !name.equals("serializedprofile") && !name.equals("playerprofile")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(target, null);
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private void invokeNullableProfileSetters(Object target) {
        for (Method method : target.getClass().getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (method.getParameterTypes().length != 1) {
                continue;
            }
            if (!name.equals("setplayerprofile") && !name.equals("setownerprofile") && !name.equals("setprofile")) {
                continue;
            }
            try {
                method.invoke(target, new Object[]{null});
            } catch (Throwable ignored) {
            }
        }
    }

    public static final class TextureData {
        private final String value;
        private final String normalized;
        private final String signature;
        private final UUID profileId;
        private final String profileName;

        private TextureData(String value, String normalized, String signature, UUID profileId, String profileName) {
            this.value = value;
            this.normalized = normalized;
            this.signature = signature;
            this.profileId = profileId;
            this.profileName = profileName;
        }

        public static TextureData empty() {
            return new TextureData(null, null, null, null, null);
        }

        public boolean hasTexture() {
            return normalized != null;
        }

        public String getValue() {
            return value;
        }

        public String getNormalized() {
            return normalized;
        }

        public String getSignature() {
            return signature;
        }

        public UUID getProfileId() {
            return profileId;
        }

        public String getProfileName() {
            return profileName;
        }
    }

    private static final class TextureAccumulator {
        private String texture;
        private String normalizedTexture;
        private String signature;
        private UUID profileId;
        private String profileName;

        private TextureData toData() {
            return new TextureData(texture, normalizedTexture, signature, profileId, profileName);
        }
    }
}
