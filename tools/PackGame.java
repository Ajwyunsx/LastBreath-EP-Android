package com.lastbreath.epretake.mobile;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.zip.Deflater;

public final class PackGame {
    private static final byte[] MAGIC = {'L', 'B', 'P', '2', 0, 0, 0, 1};
    private static final int TAG_BYTES = 16;
    private static final int IV_BYTES = 12;

    private static final class Entry {
        Path source;
        String name;
        String hash;
        String mime;
        byte[] iv;
        boolean compressed;
        long offset;
        long encryptedSize;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: PackGame <source-dir> <game.bin>");
        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        List<Entry> entries = collect(source);
        SecureRandom random = new SecureRandom();
        for (Entry entry : entries) {
            entry.iv = new byte[IV_BYTES];
            random.nextBytes(entry.iv);
        }

        String manifest = buildManifest(entries);
        byte[] manifestIv = new byte[IV_BYTES];
        random.nextBytes(manifestIv);
        byte[] encryptedManifest = crypt(true, manifestIv, manifest.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(output.getParent());
        writePack(output, entries, encryptedManifest, manifestIv);
        verifyPack(output, entries, encryptedManifest.length);
        System.out.println("Packed and verified " + entries.size() + " encrypted resources into " + output);
    }

    private static List<Entry> collect(Path source) throws Exception {
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(source)) {
            stream.filter(Files::isRegularFile).forEach(paths::add);
        }
        paths.sort(Comparator.naturalOrder());
        List<Entry> entries = new ArrayList<>();
        long offset = 0;
        for (Path path : paths) {
            Entry entry = new Entry();
            entry.source = path;
            entry.name = source.relativize(path).toString().replace(File.separatorChar, '/');
            entry.hash = hex(MessageDigest.getInstance("SHA-256").digest(entry.name.getBytes(StandardCharsets.UTF_8)));
            entry.mime = mime(entry.name);
            entry.compressed = isCompressible(entry.name);
            entry.offset = offset;
            entry.encryptedSize = encode(entry).length + TAG_BYTES;
            offset += entry.encryptedSize;
            entries.add(entry);
        }
        return entries;
    }

    private static String buildManifest(List<Entry> entries) {
        StringBuilder json = new StringBuilder("{\"entries\":{");
        boolean first = true;
        for (Entry entry : entries) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(entry.hash).append("\":{")
                    .append("\"o\":").append(entry.offset)
                    .append(",\"l\":").append(entry.encryptedSize)
                    .append(",\"c\":").append(entry.compressed ? 1 : 0)
                    .append(",\"i\":\"").append(Base64.getEncoder().encodeToString(entry.iv)).append('"')
                    .append(",\"m\":\"").append(entry.mime).append("\"}");
        }
        return json.append("}}").toString();
    }

    private static void writePack(Path output, List<Entry> entries, byte[] manifest, byte[] manifestIv) throws Exception {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(output))) {
            out.write(MAGIC);
            out.writeInt(manifest.length);
            out.write(manifestIv);
            out.write(manifest);
            for (Entry entry : entries) {
                out.write(crypt(true, entry.iv, encode(entry)));
            }
        }
    }

    private static void verifyPack(Path output, List<Entry> entries, int manifestLength) throws Exception {
        long dataStart = MAGIC.length + 4 + IV_BYTES + manifestLength;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try (java.nio.channels.FileChannel file = java.nio.channels.FileChannel.open(output)) {
            java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(8);
            file.read(header, 0);
            if (!MessageDigest.isEqual(MAGIC, header.array())) throw new IllegalStateException("Bad pack header");
            List<java.util.concurrent.Future<?>> checks = new ArrayList<>();
            for (Entry entry : entries) {
                checks.add(pool.submit(() -> {
                    if (entry.encryptedSize > Integer.MAX_VALUE) throw new IllegalStateException("Entry too large");
                    java.nio.ByteBuffer encrypted = java.nio.ByteBuffer.allocate((int) entry.encryptedSize);
                    long readOffset = dataStart + entry.offset;
                    while (encrypted.hasRemaining()) {
                        int count = file.read(encrypted, readOffset);
                        if (count < 0) throw new IllegalStateException("Unexpected end of pack");
                        readOffset += count;
                    }
                    byte[] packed = crypt(false, entry.iv, encrypted.array());
                    byte[] actual = entry.compressed ? inflate(packed) : packed;
                    byte[] expected = Files.readAllBytes(entry.source);
                    if (!MessageDigest.isEqual(actual, expected)) {
                        throw new IllegalStateException("Verification failed: " + entry.name);
                    }
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> check : checks) check.get();
        } finally {
            pool.shutdownNow();
        }
    }

    private static byte[] encode(Entry entry) throws Exception {
        byte[] input = Files.readAllBytes(entry.source);
        if (!entry.compressed) return input;
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(64, input.length / 3));
            byte[] buffer = new byte[32768];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] input) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        try {
            inflater.setInput(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(64, input.length * 4));
            byte[] buffer = new byte[32768];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) throw new IllegalStateException("Invalid compressed entry");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static boolean isCompressible(String name) {
        String mime = mime(name);
        return mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("svg");
    }

    private static byte[] crypt(boolean encrypt, byte[] iv, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                new SecretKeySpec(PackKey.material(), "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input);
    }

    private static String mime(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "json" -> "application/json";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            default -> "application/octet-stream";
        };
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }
}
