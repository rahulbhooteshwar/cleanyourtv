package com.gathin.tvhome;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small HTTPS downloads for the theme gallery: plain GETs, and files cached in the app's cache folder by URL. */
final class WebFiles {

    private WebFiles() {
    }

    static byte[] get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        c.setRequestProperty("Accept", "application/vnd.github+json, */*");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " " + url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
                return bytes.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** The file at {@code url}, downloaded once into cache/web/; null when it can't be fetched. Not on the main thread. */
    static File cached(Context context, String url) {
        if (url == null) return null;
        File dir = new File(context.getCacheDir(), "web");
        File f = new File(dir, hash(url));
        if (f.isFile() && f.length() > 0) return f;
        try {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            write(f, get(url));
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("rename " + f);
        }
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
