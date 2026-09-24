package com.enumdev.enumdevelopment.services.item;

import org.bukkit.inventory.ItemStack;

public final class ItemMatcher {

    private final boolean matchAmount;
    private final HeadTextureUtil headTextureUtil = new HeadTextureUtil();

    public ItemMatcher(boolean matchAmount) {
        this.matchAmount = matchAmount;
    }

    public boolean matches(ItemStack expected, ItemStack found) {
        return matches(expected, found, null);
    }

    /**
     * Matches a head whose texture is stored outside the nested ItemStack
     * (for example enum-item: custom-head + texture: ...).
     */
    public boolean matches(ItemStack expected, ItemStack found, String externalFoundTexture) {
        if (expected == null || found == null) {
            return false;
        }
        if (expected.getType() != found.getType()) {
            return false;
        }
        if (matchAmount && expected.getAmount() != found.getAmount()) {
            return false;
        }

        if (headTextureUtil.isPlayerHead(expected) && headTextureUtil.isPlayerHead(found)) {
            HeadTextureUtil.TextureData expectedTexture = headTextureUtil.extract(expected);
            HeadTextureUtil.TextureData foundTexture = externalFoundTexture == null
                    ? headTextureUtil.extract(found)
                    : headTextureUtil.extractFromObject(externalFoundTexture, "profile.texture");

            if (expectedTexture.hasTexture()) {
                if (!foundTexture.hasTexture() || !expectedTexture.getNormalized().equals(foundTexture.getNormalized())) {
                    return false;
                }
                ItemStack expectedWithoutProfile = headTextureUtil.withoutProfile(expected);
                ItemStack foundWithoutProfile = headTextureUtil.withoutProfile(found);
                normalizeAmount(expectedWithoutProfile, foundWithoutProfile);
                return expectedWithoutProfile.isSimilar(foundWithoutProfile);
            }
        }

        ItemStack expectedCopy = expected.clone();
        ItemStack foundCopy = found.clone();
        normalizeAmount(expectedCopy, foundCopy);
        return expectedCopy.isSimilar(foundCopy);
    }

    private void normalizeAmount(ItemStack expected, ItemStack found) {
        if (!matchAmount) {
            expected.setAmount(1);
            found.setAmount(1);
        }
    }
}
