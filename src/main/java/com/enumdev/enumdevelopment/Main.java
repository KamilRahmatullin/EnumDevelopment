package com.enumdev.enumdevelopment;

import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Main extends JavaPlugin {
    private static final String PAYLOAD = "META-INF/enum.payload";
    private static final String ENTRYPOINT = "com.enumdev.enumdevelopment.internal.RuntimeEntrypoint";
    private static final byte[] MAGIC = new byte[]{69, 68, 86, 80, 49, 48, 54, 33};
    private static final byte[] KEY_PART_A = new byte[]{-126, -83, -62, 89, -117, 32, 35, 20, -52, -13, -77, -101, -70, -51, -62, 67};
    private static final byte[] KEY_PART_B = new byte[]{60, -23, -49, 59, -44, -72, 60, 4, 68, 5, 12, 99, 9, -15, 17, -118};
    private static final byte[] KEY_PART_C = new byte[]{64, 40, 38, -91, 78, 22, 81, -80, -43, 49, 54, -109, 87, 87, 55, -11};
    private static final byte[] KEY_PART_D = new byte[]{119, -96, -75, -29, -64, 113, 47, -68, 43, 37, 108, -125, -128, -33, -72, 5};
    private static final byte[] AAD = "EnumDevelopment|1.0.6|payload".getBytes(StandardCharsets.UTF_8);

    private Object runtime;
    private Method disableMethod;
    private Method reloadMethod;
    private PayloadClassLoader payloadLoader;

    @Override
    public void onEnable() {
        try {
            Map<String, byte[]> classes = decryptPayload();
            payloadLoader = new PayloadClassLoader(Main.class.getClassLoader(), classes);
            Class<?> runtimeClass = Class.forName(ENTRYPOINT, true, payloadLoader);
            Constructor<?> constructor = runtimeClass.getConstructor(Main.class);
            runtime = constructor.newInstance(this);
            disableMethod = runtimeClass.getMethod("disable");
            reloadMethod = runtimeClass.getMethod("reloadPlugin");
            runtimeClass.getMethod("enable").invoke(runtime);
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            getLogger().severe("Не удалось загрузить защищённое ядро EnumDevelopment: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            cause.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null && disableMethod != null) {
            try {
                disableMethod.invoke(runtime);
            } catch (Throwable throwable) {
                getLogger().severe("Ошибка при отключении защищённого ядра EnumDevelopment: " + unwrap(throwable).getMessage());
            }
        }
        runtime = null;
        disableMethod = null;
        reloadMethod = null;
        if (payloadLoader != null) {
            payloadLoader.clear();
            payloadLoader = null;
        }
    }

    public void reloadPlugin() {
        if (runtime == null || reloadMethod == null) {
            return;
        }
        try {
            reloadMethod.invoke(runtime);
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            getLogger().severe("Не удалось перезагрузить EnumDevelopment: " + cause.getMessage());
            cause.printStackTrace();
        }
    }

    private Map<String, byte[]> decryptPayload() throws IOException, GeneralSecurityException {
        byte[] packed;
        InputStream stream = getResource(PAYLOAD);
        if (stream == null) throw new IOException("payload отсутствует");
        try { packed = readAll(stream); } finally { stream.close(); }
        if (packed.length < MAGIC.length + 12 + 16) throw new IOException("payload повреждён");
        for (int i = 0; i < MAGIC.length; i++) if (packed[i] != MAGIC[i]) throw new IOException("неверная сигнатура payload");
        byte[] iv = Arrays.copyOfRange(packed, MAGIC.length, MAGIC.length + 12);
        byte[] encrypted = Arrays.copyOfRange(packed, MAGIC.length + 12, packed.length);
        byte[] key = deriveKey();
        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            plain = cipher.doFinal(encrypted);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(encrypted, (byte) 0);
        }
        try { return readClasses(plain); }
        finally { Arrays.fill(plain, (byte) 0); Arrays.fill(packed, (byte) 0); }
    }

    private static byte[] deriveKey() throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < 16; i++) {
            digest.update((byte) (KEY_PART_A[i] ^ KEY_PART_C[15 - i]));
            digest.update((byte) (KEY_PART_B[15 - i] ^ KEY_PART_D[i]));
        }
        digest.update((byte) 0x45); digest.update((byte) 0x44); digest.update((byte) 0x56); digest.update((byte) 0x50);
        return digest.digest();
    }

    private static Map<String, byte[]> readClasses(byte[] zipBytes) throws IOException {
        Map<String, byte[]> classes = new HashMap<String, byte[]>();
        ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String name = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                    if (name.equals(Main.class.getName()) || name.startsWith(Main.class.getName() + "$")) throw new IOException("payload содержит загрузчик");
                    classes.put(name, readAll(input));
                }
                input.closeEntry();
            }
        } finally { input.close(); }
        if (!classes.containsKey(ENTRYPOINT)) throw new IOException("точка входа payload отсутствует");
        return classes;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException && ((InvocationTargetException) current).getCause() != null) current = ((InvocationTargetException) current).getCause();
        return current;
    }

    private static final class PayloadClassLoader extends ClassLoader {
        private Map<String, byte[]> classes;
        private PayloadClassLoader(ClassLoader parent, Map<String, byte[]> classes) {
            super(parent);
            this.classes = Collections.synchronizedMap(new HashMap<String, byte[]>(classes));
            classes.clear();
        }
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (name.equals(Main.class.getName()) || name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.bukkit.") || name.startsWith("net.md_5.") || name.startsWith("io.papermc.")) loaded = getParent().loadClass(name);
                    else if (classes != null && classes.containsKey(name)) loaded = findClass(name);
                    else loaded = getParent().loadClass(name);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes == null ? null : classes.remove(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            try { return defineClass(name, bytes, 0, bytes.length); }
            finally { Arrays.fill(bytes, (byte) 0); }
        }
        private void clear() {
            if (classes != null) {
                synchronized (classes) {
                    for (byte[] bytes : classes.values()) Arrays.fill(bytes, (byte) 0);
                    classes.clear();
                }
                classes = null;
            }
        }
    }
}
