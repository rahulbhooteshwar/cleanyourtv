package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The themes in the original CleanYourTV repository, to install from Launcher settings > Theme. One request to
 * GitHub's git tree API lists every file of the repository; the themes/<name>/ folders with a config.json are the
 * gallery, cached for a day (GitHub allows 60 such requests an hour per address), and kept when offline. Files come
 * from raw.githubusercontent.com. An installed theme loses the keys that would undo this TV's own layout.
 */
final class ThemeGallery {

    private static final String REPO = "Gathin23/CleanYourTV";
    private static final String BRANCH = "main";
    private static final String TREE = "https://api.github.com/repos/" + REPO + "/git/trees/" + BRANCH + "?recursive=1";
    static final String RAW = "https://raw.githubusercontent.com/" + REPO + "/" + BRANCH + "/";
    private static final long MAX_AGE_MS = 24L * 3600 * 1000;
    /** Keys a library theme may not bring: the dock, the grid and the hidden apps belong to this TV. */
    private static final String[] PERSONAL = {"dock_apps", "columns", "rows_below_dock", "hidden_apps", "top_bar_apps",
            "live_tv_package", "live_tv_input_id"};

    static final class Online {
        final String name;
        /** Paths under themes/<name>/: config.json, wallpaper.jpg, screenshot.jpg, wallpapers/... */
        final List<String> files;

        Online(String name, List<String> files) {
            this.name = name;
            this.files = files;
        }

        /** The screenshot (or failing that the wallpaper) to show on the tile. */
        String previewUrl() {
            if (files.contains("screenshot.jpg")) return RAW + "themes/" + name + "/screenshot.jpg";
            if (files.contains("wallpaper.jpg")) return RAW + "themes/" + name + "/wallpaper.jpg";
            for (String f : files) if (f.startsWith("wallpapers/") && ThemeFiles.collectionImage(f.substring(11))) return RAW + "themes/" + name + "/" + f;
            return null;
        }
    }

    private ThemeGallery() {
    }

    /** The gallery, from the day-old cache or GitHub; empty when neither is available. Not on the main thread. */
    static List<Online> load(Context context) {
        File cache = new File(context.getFilesDir(), "theme-gallery.json");
        JSONObject cached = read(cache);
        boolean fresh = cached != null && System.currentTimeMillis() - cached.optLong("time") < MAX_AGE_MS;
        if (!fresh) {
            try {
                JSONObject tree = new JSONObject(new String(WebFiles.get(TREE), StandardCharsets.UTF_8));
                JSONObject themes = new JSONObject();
                JSONArray entries = tree.getJSONArray("tree");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject e = entries.getJSONObject(i);
                    String path = e.optString("path");
                    if (!"blob".equals(e.optString("type")) || !path.startsWith("themes/")) continue;
                    String[] parts = path.split("/", 3);
                    if (parts.length < 3 || !ThemeFiles.themeName(parts[1])) continue;
                    JSONArray files = themes.optJSONArray(parts[1]);
                    if (files == null) themes.put(parts[1], files = new JSONArray());
                    files.put(parts[2]);
                }
                cached = new JSONObject().put("time", System.currentTimeMillis()).put("themes", themes);
                WebFiles.write(cache, cached.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                // offline or rate-limited: keep a stale list, if there is one
            }
        }
        List<Online> list = new ArrayList<>();
        if (cached == null) return list;
        JSONObject themes = cached.optJSONObject("themes");
        if (themes == null) return list;
        Map<String, List<String>> sorted = new TreeMap<>();
        for (Iterator<String> it = themes.keys(); it.hasNext(); ) {
            String name = it.next();
            JSONArray files = themes.optJSONArray(name);
            List<String> paths = new ArrayList<>();
            for (int i = 0; files != null && i < files.length(); i++) paths.add(files.optString(i));
            if (paths.contains(Config.FILE_NAME)) sorted.put(name, paths);
        }
        for (Map.Entry<String, List<String>> e : sorted.entrySet()) list.add(new Online(e.getKey(), e.getValue()));
        return list;
    }

    /** Downloads {@code theme} into the library (themes/<name>/). Not on the main thread. */
    static boolean install(Context context, Online theme) {
        File dir = Config.dir(context);
        if (dir == null) return false;
        File target = new File(new File(dir, ThemeFiles.LIBRARY), theme.name);
        String base = RAW + "themes/" + theme.name + "/";
        try {
            File collection = new File(target, ThemeFiles.WALLPAPERS);
            if (!target.isDirectory() && !target.mkdirs()) return false;
            JSONObject config = new JSONObject(new String(WebFiles.get(base + Config.FILE_NAME), StandardCharsets.UTF_8));
            for (String key : PERSONAL) config.remove(key);
            for (String f : theme.files) {
                if (f.equals("wallpaper.jpg")) WebFiles.write(new File(target, f), WebFiles.get(base + f));
                else if (f.equals("screenshot.jpg")) WebFiles.write(new File(target, ThemeFiles.PREVIEW), WebFiles.get(base + f));
                else if (f.startsWith("wallpapers/")) {
                    String name = f.substring("wallpapers/".length());
                    if (!ThemeFiles.collectionImage(name) && !WallpaperCaptions.FILE_NAME.equals(name)) continue;
                    //noinspection ResultOfMethodCallIgnored
                    collection.mkdirs();
                    WebFiles.write(new File(collection, name), WebFiles.get(base + f));
                }
            }
            // config.json last: a theme without it is not listed, so a broken download never shows up half-done.
            WebFiles.write(new File(target, Config.FILE_NAME), config.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static JSONObject read(File f) {
        try {
            return f.isFile() ? new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : null;
        } catch (IOException | org.json.JSONException e) {
            return null;
        }
    }
}
