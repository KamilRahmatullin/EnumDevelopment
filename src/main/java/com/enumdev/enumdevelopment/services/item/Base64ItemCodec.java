package com.enumdev.enumdevelopment.services.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class Base64ItemCodec {

    public DecodedItems decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        byte[] data = decodeBytes(value);
        if (data == null || data.length == 0) {
            return null;
        }

        DecodedItems objectDecoded = decodeObject(data);
        if (objectDecoded != null) {
            return objectDecoded;
        }
        return decodeSizePrefixedArray(data);
    }

    public String encode(DecodedItems decoded, ItemStack[] items) throws IOException {
        if (decoded.getKind() == EncodedKind.SIZE_PREFIXED_ARRAY) {
            return encodeSizePrefixedArray(items, decoded.isUrlSafe());
        }
        if (decoded.getKind() == EncodedKind.ARRAY_OBJECT) {
            return encodeObject(items, decoded.isUrlSafe());
        }
        return encodeObject(items.length == 0 ? null : items[0], decoded.isUrlSafe());
    }

    private DecodedItems decodeObject(byte[] data) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            Object object = input.readObject();
            if (object instanceof ItemStack) {
                return DecodedItems.single((ItemStack) object, false);
            }
            if (object instanceof ItemStack[]) {
                return DecodedItems.array((ItemStack[]) object, false);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private DecodedItems decodeSizePrefixedArray(byte[] data) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            int size = input.readInt();
            if (size < 0 || size > 10000) {
                return null;
            }
            ItemStack[] items = new ItemStack[size];
            for (int index = 0; index < size; index++) {
                Object object = input.readObject();
                if (object != null && !(object instanceof ItemStack)) {
                    return null;
                }
                items[index] = (ItemStack) object;
            }
            return DecodedItems.sizePrefixedArray(items, false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] decodeBytes(String value) {
        String normalized = value.trim();
        boolean urlSafe = normalized.indexOf('-') >= 0 || normalized.indexOf('_') >= 0;
        String padded = pad(normalized);
        try {
            return (urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(padded);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getDecoder().decode(padded);
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private String encodeObject(Object object, boolean urlSafe) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return encodeBytes(bytes.toByteArray(), urlSafe);
    }

    private String encodeSizePrefixedArray(ItemStack[] items, boolean urlSafe) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) {
                output.writeObject(item);
            }
        }
        return encodeBytes(bytes.toByteArray(), urlSafe);
    }

    private String encodeBytes(byte[] data, boolean urlSafe) {
        return urlSafe ? Base64.getUrlEncoder().encodeToString(data) : Base64.getEncoder().encodeToString(data);
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

    public enum EncodedKind {
        SINGLE_OBJECT,
        ARRAY_OBJECT,
        SIZE_PREFIXED_ARRAY
    }

    public static final class DecodedItems {
        private final EncodedKind kind;
        private final ItemStack[] items;
        private final boolean urlSafe;

        private DecodedItems(EncodedKind kind, ItemStack[] items, boolean urlSafe) {
            this.kind = kind;
            this.items = copy(items);
            this.urlSafe = urlSafe;
        }

        public static DecodedItems single(ItemStack item, boolean urlSafe) {
            return new DecodedItems(EncodedKind.SINGLE_OBJECT, new ItemStack[]{item}, urlSafe);
        }

        public static DecodedItems array(ItemStack[] items, boolean urlSafe) {
            return new DecodedItems(EncodedKind.ARRAY_OBJECT, items, urlSafe);
        }

        public static DecodedItems sizePrefixedArray(ItemStack[] items, boolean urlSafe) {
            return new DecodedItems(EncodedKind.SIZE_PREFIXED_ARRAY, items, urlSafe);
        }

        public EncodedKind getKind() {
            return kind;
        }

        public ItemStack[] getItems() {
            return copy(items);
        }

        public boolean isUrlSafe() {
            return urlSafe;
        }

        private static ItemStack[] copy(ItemStack[] input) {
            if (input == null) {
                return new ItemStack[0];
            }
            ItemStack[] result = new ItemStack[input.length];
            for (int index = 0; index < input.length; index++) {
                result[index] = input[index] == null ? null : input[index].clone();
            }
            return result;
        }
    }
}
