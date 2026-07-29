package com.enumdev.enumdevelopment.services.item;

import org.bukkit.inventory.ItemStack;

public final class ItemMatcher {

    private final boolean matchAmount;

    public ItemMatcher(boolean matchAmount) {
        this.matchAmount = matchAmount;
    }

    public boolean matches(ItemStack expected, ItemStack found) {
        if (expected == null || found == null) {
            return false;
        }
        if (expected.getType() != found.getType()) {
            return false;
        }
        if (matchAmount && expected.getAmount() != found.getAmount()) {
            return false;
        }

        ItemStack expectedCopy = expected.clone();
        ItemStack foundCopy = found.clone();
        if (!matchAmount) {
            expectedCopy.setAmount(1);
            foundCopy.setAmount(1);
        }
        return expectedCopy.isSimilar(foundCopy);
    }
}
