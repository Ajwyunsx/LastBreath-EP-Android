package com.lastbreath.epretake.mobile;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class GameBundle implements AutoCloseable {
    private static final byte[] MAGIC = {'L', 'B', 'P', '2', 0, 0, 0, 1};
    private static final int IV_BYTES = 12;
    private static final String PREFIX = "https://game.local/";

    private final AssetFileDescriptor asset;
    private final long dataStart;
    private final JSONObject entries;
    private final LruCache<String, byte[]> cache = new LruCache<String, byte[]>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, byte[] value) {
            return value.length;
        }
    };

    GameBundle(AssetManager assets) throws Exception {
        asset = assets.openFd("game.bin");
        byte[] header = readAt(asset.getStartOffset(), 24);
        ByteBuffer buffer = ByteBuffer.wrap(header);
        byte[] magic = new byte[8];
        buffer.get(magic);
        if (!MessageDigest.isEqual(MAGIC, magic)) throw new IOException("Invalid game bundle");
        int manifestLength = buffer.getInt();
        if (manifestLength <= 0 || manifestLength > 16 * 1024 * 1024) {
            throw new IOException("Invalid bundle index");
        }
        byte[] manifestIv = new byte[IV_BYTES];
        buffer.get(manifestIv);
        byte[] encryptedManifest = readAt(asset.getStartOffset() + 24, manifestLength);
        byte[] manifest = decrypt(manifestIv, encryptedManifest);
        entries = new JSONObject(new String(manifest, StandardCharsets.UTF_8)).getJSONObject("entries");
        dataStart = asset.getStartOffset() + 24 + manifestLength;
    }

    boolean handles(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    WebResourceResponse open(String url) {
        try {
            String path = android.net.Uri.parse(url).getPath();
            if (path == null) return failure(404, "Missing resource path");
            path = path.startsWith("/") ? path.substring(1) : path;
            if (path.isEmpty()) path = "index.html";
            String hash = hex(MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8)));
            JSONObject entry = entries.optJSONObject(hash);
            if (entry == null) return failure(404, "Resource not found: " + path);

            byte[] plain = cache.get(hash);
            if (plain == null) {
                byte[] iv = Base64.decode(entry.getString("i"), Base64.DEFAULT);
                byte[] encrypted = readAt(dataStart + entry.getLong("o"), entry.getInt("l"));
                plain = decrypt(iv, encrypted);
                if (entry.optInt("c") == 1) plain = inflate(plain);
                if (plain.length <= 512 * 1024) cache.put(hash, plain);
            }
            String mime = entry.getString("m");
            String encoding = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("svg")
                    ? "UTF-8" : null;
            return new WebResourceResponse(mime, encoding, 200, "OK", headers(plain.length),
                    new ByteArrayInputStream(plain));
        } catch (Exception error) {
            Log.e("LastBreath", "Failed to serve " + url, error);
            return failure(500, "Unable to decrypt resource");
        }
    }

    private byte[] readAt(long offset, int length) throws IOException {
        ParcelFileDescriptor duplicate = null;
        FileInputStream input = null;
        try {
            duplicate = ParcelFileDescriptor.dup(asset.getFileDescriptor());
            input = new FileInputStream(duplicate.getFileDescriptor());
            java.nio.channels.FileChannel channel = input.getChannel();
            ByteBuffer data = ByteBuffer.allocate(length);
            long readOffset = offset;
            while (data.hasRemaining()) {
                int count = channel.read(data, readOffset);
                if (count < 0) throw new IOException("Unexpected end of game bundle");
                readOffset += count;
            }
            return data.array();
        } finally {
            if (input != null) input.close();
            else if (duplicate != null) duplicate.close();
        }
    }

    private static byte[] decrypt(byte[] iv, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(PackKey.material(), "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input);
    }

    private static byte[] inflate(byte[] input) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        try {
            inflater.setInput(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(64, input.length * 4));
            byte[] buffer = new byte[32768];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) throw new IOException("Invalid compressed entry");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static WebResourceResponse failure(int status, String message) {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        String reason = status == 404 ? "Not Found" : "Internal Server Error";
        return new WebResourceResponse("text/plain", "UTF-8", status, reason, headers(data.length),
                new ByteArrayInputStream(data));
    }

    private static Map<String, String> headers(int contentLength) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Length", String.valueOf(contentLength));
        return headers;
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }
        return new String(result);
    }

    @Override
    public void close() throws IOException {
        asset.close();
    }
}
