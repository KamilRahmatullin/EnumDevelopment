package com.enumdev.enumdevelopment.services.item;

import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supports head identifiers embedded in ordinary strings. Besides plugin-style
 * basehead values, it recognizes exact texture payloads inside JSON, SNBT and
 * modern component serialization. This is intentionally texture-aware: random
 * Base64 blobs are never changed unless they decode to the target skin.
 */
public final class FlexibleItemStringCodec {

    private static final Pattern CUSTOM_HEAD = Pattern.compile(
            "(?i)(basehead|base-head|playerhead|player-head|customhead|custom-head|head)(\\s*[-:=_]\\s*)(https?://textures\\.minecraft\\.net/texture/[0-9a-z_-]{16,}|[0-9a-f]{32,64}|[A-Za-z0-9+/_=-]{40,})"
    );

    private static final Pattern TEXTURE_TOKEN = Pattern.compile(
            "(?i)https?://textures\\.minecraft\\.net/texture/[0-9a-z_-]{16,}"
                    + "|(?<![A-Za-z0-9])[0-9a-f]{32,64}(?![A-Za-z0-9])"
                    + "|(?<![A-Za-z0-9+/_=])[A-Za-z0-9+/_-]{40,}={0,2}(?![A-Za-z0-9+/_=-])"
    );

    private final HeadTextureUtil headTextureUtil;

    public FlexibleItemStringCodec(HeadTextureUtil headTextureUtil) {
        this.headTextureUtil = headTextureUtil;
    }

    public ProcessResult process(String input, ItemStack target, ItemStack replacement, boolean replaceMode) {
        if (input == null || input.isEmpty() || !headTextureUtil.isPlayerHead(target)) {
            return ProcessResult.empty(input);
        }
        HeadTextureUtil.TextureData targetTexture = headTextureUtil.extract(target);
        if (!targetTexture.hasTexture()) {
            return ProcessResult.empty(input);
        }

        ProcessResult custom = processCustomHeadIdentifiers(input, targetTexture, replacement, replaceMode);

        // A scalar basehead can be converted to a normal material, but a lone
        // profile/texture field cannot safely represent a non-head item.
        if (replaceMode && replacement != null && !headTextureUtil.isPlayerHead(replacement)) {
            return custom;
        }

        HeadTextureUtil.TextureData replacementTexture = replacement == null
                ? HeadTextureUtil.TextureData.empty()
                : headTextureUtil.extract(replacement);
        ProcessResult embedded = processTextureTokens(custom.getValue(), targetTexture, replacementTexture, replaceMode);
        return new ProcessResult(
                embedded.getValue(),
                custom.getMatches() + embedded.getMatches(),
                custom.isChanged() || embedded.isChanged()
        );
    }

    private ProcessResult processCustomHeadIdentifiers(
            String input,
            HeadTextureUtil.TextureData targetTexture,
            ItemStack replacement,
            boolean replaceMode
    ) {
        Matcher matcher = CUSTOM_HEAD.matcher(input);
        StringBuffer output = null;
        int matches = 0;
        while (matcher.find()) {
            String foundTexture = matcher.group(3);
            String normalized = headTextureUtil.normalizeTexture(foundTexture);
            if (!targetTexture.getNormalized().equals(normalized)) {
                continue;
            }
            matches++;
            if (!replaceMode || replacement == null) {
                continue;
            }

            String replacementValue;
            if (headTextureUtil.isPlayerHead(replacement)) {
                HeadTextureUtil.TextureData replacementTexture = headTextureUtil.extract(replacement);
                if (!replacementTexture.hasTexture() || replacementTexture.getValue() == null) {
                    continue;
                }
                String represented = preserveRepresentation(foundTexture, replacementTexture.getValue());
                if (represented == null) {
                    continue;
                }
                replacementValue = matcher.group(1) + matcher.group(2) + represented;
            } else {
                replacementValue = replacement.getType().name();
            }

            if (matcher.group().equals(replacementValue)) {
                continue;
            }
            if (output == null) {
                output = new StringBuffer(input.length() + 32);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacementValue));
        }

        if (matches == 0) {
            return ProcessResult.empty(input);
        }
        if (!replaceMode || output == null) {
            return new ProcessResult(input, matches, false);
        }
        matcher.appendTail(output);
        return new ProcessResult(output.toString(), matches, true);
    }

    private ProcessResult processTextureTokens(
            String input,
            HeadTextureUtil.TextureData targetTexture,
            HeadTextureUtil.TextureData replacementTexture,
            boolean replaceMode
    ) {
        Matcher matcher = TEXTURE_TOKEN.matcher(input);
        StringBuffer output = null;
        int matches = 0;
        while (matcher.find()) {
            String foundTexture = matcher.group();
            String normalized = headTextureUtil.normalizeTexture(foundTexture);
            if (!targetTexture.getNormalized().equals(normalized)) {
                continue;
            }
            matches++;
            if (!replaceMode || !replacementTexture.hasTexture() || replacementTexture.getValue() == null) {
                continue;
            }

            String replacementValue = preserveRepresentation(foundTexture, replacementTexture.getValue());
            if (replacementValue == null || foundTexture.equals(replacementValue)) {
                continue;
            }
            if (output == null) {
                output = new StringBuffer(input.length() + 32);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacementValue));
        }

        if (matches == 0) {
            return ProcessResult.empty(input);
        }
        if (!replaceMode || output == null) {
            return new ProcessResult(input, matches, false);
        }
        matcher.appendTail(output);
        return new ProcessResult(output.toString(), matches, true);
    }

    private String preserveRepresentation(String originalTexture, String replacementTexture) {
        String lower = originalTexture.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            String hash = headTextureUtil.textureHash(replacementTexture);
            return hash == null ? null : "https://textures.minecraft.net/texture/" + hash;
        }
        if (originalTexture.matches("(?i)[0-9a-f]{32,64}")) {
            return headTextureUtil.textureHash(replacementTexture);
        }
        return replacementTexture;
    }

    public static final class ProcessResult {
        private final String value;
        private final int matches;
        private final boolean changed;

        private ProcessResult(String value, int matches, boolean changed) {
            this.value = value;
            this.matches = matches;
            this.changed = changed;
        }

        private static ProcessResult empty(String value) {
            return new ProcessResult(value, 0, false);
        }

        public String getValue() {
            return value;
        }

        public int getMatches() {
            return matches;
        }

        public boolean isChanged() {
            return changed;
        }
    }
}
