package com.enumdev.enumdevelopment.services.item;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Base64;

/** Decodes the common Bukkit/Paper inventory encodings used by plugins. */
public final class Base64ItemCodec {

    public DecodedItems decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        DecodedBytes decodedBytes = decodeBytes(value);
        if (decodedBytes == null || decodedBytes.data.length == 0) {
            return null;
        }

        DecodedItems objectDecoded = decodeObject(decodedBytes.data, decodedBytes.urlSafe);
        if (objectDecoded != null) {
            return objectDecoded;
        }
        DecodedItems arrayDecoded = decodeSizePrefixedArray(decodedBytes.data, decodedBytes.urlSafe);
        if (arrayDecoded != null) {
            return arrayDecoded;
        }
        DecodedItems modernDecoded = decodeModernItemBytes(decodedBytes.data, decodedBytes.urlSafe);
        if (modernDecoded != null) {
            return modernDecoded;
        }
        return decodeUnsafeItemBytes(decodedBytes.data, decodedBytes.urlSafe);
    }

    public String encode(DecodedItems decoded, ItemStack[] items) throws IOException {
        if (decoded.getKind() == EncodedKind.SIZE_PREFIXED_ARRAY) {
            return encodeSizePrefixedArray(items, decoded.isUrlSafe());
        }
        if (decoded.getKind() == EncodedKind.ARRAY_OBJECT) {
            return encodeObject(items, decoded.isUrlSafe());
        }
        if (decoded.getKind() == EncodedKind.MODERN_ITEM_BYTES) {
            return encodeModernItemBytes(first(items), decoded.isUrlSafe());
        }
        if (decoded.getKind() == EncodedKind.UNSAFE_ITEM_BYTES) {
            return encodeUnsafeItemBytes(first(items), decoded.isUrlSafe());
        }
        return encodeObject(first(items), decoded.isUrlSafe());
    }

    private ItemStack first(ItemStack[] items) {
        return items == null || items.length == 0 ? null : items[0];
    }

    private DecodedItems decodeObject(byte[] data, boolean urlSafe) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            Object object = input.readObject();
            if (object instanceof ItemStack) {
                return DecodedItems.single((ItemStack) object, urlSafe);
            }
            if (object instanceof ItemStack[]) {
                return DecodedItems.array((ItemStack[]) object, urlSafe);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private DecodedItems decodeSizePrefixedArray(byte[] data, boolean urlSafe) {
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
            return DecodedItems.sizePrefixedArray(items, urlSafe);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DecodedItems decodeModernItemBytes(byte[] data, boolean urlSafe) {
        try {
            Method method = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            Object item = method.invoke(null, new Object[]{data});
            if (item instanceof ItemStack) {
                return DecodedItems.modernBytes((ItemStack) item, urlSafe);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private DecodedItems decodeUnsafeItemBytes(byte[] data, boolean urlSafe) {
        try {
            Object unsafe = Bukkit.getUnsafe();
            Method method = unsafe.getClass().getMethod("deserializeItem", byte[].class);
            Object item = method.invoke(unsafe, new Object[]{data});
            if (item instanceof ItemStack) {
                return DecodedItems.unsafeBytes((ItemStack) item, urlSafe);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private DecodedBytes decodeBytes(String value) {
        String normalized = value.trim();
        boolean urlSafe = normalized.indexOf('-') >= 0 || normalized.indexOf('_') >= 0;
        String padded = pad(normalized);
        try {
            return new DecodedBytes((urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(padded), urlSafe);
        } catch (IllegalArgumentException ignored) {
            try {
                return new DecodedBytes(Base64.getMimeDecoder().decode(padded), false);
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

    private String encodeModernItemBytes(ItemStack item, boolean urlSafe) throws IOException {
        if (item == null) {
            throw new IOException("Cannot encode a null ItemStack with serializeAsBytes");
        }
        try {
            Method method = item.getClass().getMethod("serializeAsBytes");
            Object result = method.invoke(item);
            if (!(result instanceof byte[])) {
                throw new IOException("serializeAsBytes returned an unsupported value");
            }
            return encodeBytes((byte[]) result, urlSafe);
        } catch (IOException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IOException("serializeAsBytes is unavailable", exception);
        }
    }

    private String encodeUnsafeItemBytes(ItemStack item, boolean urlSafe) throws IOException {
        if (item == null) {
            throw new IOException("Cannot encode a null ItemStack with BukkitUnsafe");
        }
        try {
            Object unsafe = Bukkit.getUnsafe();
            Method method = unsafe.getClass().getMethod("serializeItem", ItemStack.class);
            Object result = method.invoke(unsafe, item);
            if (!(result instanceof byte[])) {
                throw new IOException("serializeItem returned an unsupported value");
            }
            return encodeBytes((byte[]) result, urlSafe);
        } catch (IOException exception) {
            throw exception;
        } catch (Throwable exception) {
            throw new IOException("BukkitUnsafe item serialization is unavailable", exception);
        }
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
        SIZE_PREFIXED_ARRAY,
        MODERN_ITEM_BYTES,
        UNSAFE_ITEM_BYTES
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

        public static DecodedItems modernBytes(ItemStack item, boolean urlSafe) {
            return new DecodedItems(EncodedKind.MODERN_ITEM_BYTES, new ItemStack[]{item}, urlSafe);
        }

        public static DecodedItems unsafeBytes(ItemStack item, boolean urlSafe) {
            return new DecodedItems(EncodedKind.UNSAFE_ITEM_BYTES, new ItemStack[]{item}, urlSafe);
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

    private static final class DecodedBytes {
        private final byte[] data;
        private final boolean urlSafe;

        private DecodedBytes(byte[] data, boolean urlSafe) {
            this.data = data;
            this.urlSafe = urlSafe;
        }
    }
}
