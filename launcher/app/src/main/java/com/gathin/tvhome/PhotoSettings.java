package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The built-in photo screensaver's choices (PhotoDream, PhotoShow, PhotoDreamSettingsActivity): which folder, all of
 * its photos or a picked few, how often they change, and whether a clock and a small calendar show, and where.
 * Photos are read from shared storage with the storage permission the wallpaper picker already uses.
 */
final class PhotoSettings {

    static final String PREFS = "photo_dream";
    static final String[] POSITIONS = {"tl", "tr", "c", "bl", "br"};
    static final int[] SECONDS = {3, 5, 10, 20, 60, 300};
    private static final int MAX_PHOTOS = 300;

    private final SharedPreferences prefs;

    PhotoSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Pictures/Screensaver until another folder is picked. */
    File folder() {
        String path = prefs.getString("folder", null);
        return path != null ? new File(path) : defaultFolder();
    }

    static File defaultFolder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screensaver");
    }

    void setFolder(File folder) {
        prefs.edit().putString("folder", folder.getPath()).remove("selected").apply();
    }

    /** Names picked in the folder; empty means every photo in it. */
    Set<String> selected() {
        return new HashSet<>(prefs.getStringSet("selected", new HashSet<>()));
    }

    void setSelected(Set<String> names) {
        prefs.edit().putStringSet("selected", new LinkedHashSet<>(names)).apply();
    }

    int seconds() {
        return prefs.getInt("seconds", 20);
    }

    void setSeconds(int s) {
        prefs.edit().putInt("seconds", s).apply();
    }

    boolean clock() {
        return prefs.getBoolean("clock", true);
    }

    void setClock(boolean on) {
        prefs.edit().putBoolean("clock", on).apply();
    }

    boolean calendar() {
        return prefs.getBoolean("calendar", true);
    }

    void setCalendar(boolean on) {
        prefs.edit().putBoolean("calendar", on).apply();
    }

    /** tl, tr, c, bl or br. */
    String position() {
        return prefs.getString("position", "br");
    }

    void setPosition(String p) {
        prefs.edit().putString("position", p).apply();
    }

    // ---- background audio ----

    static final int[] VOLUMES = {10, 25, 50, 75, 100};

    /** An audio file, or a folder whose tracks play in turn; null for no audio. */
    File audio() {
        String path = prefs.getString("audio", null);
        return path == null ? null : new File(path);
    }

    void setAudio(File fileOrFolder) {
        if (fileOrFolder == null) prefs.edit().remove("audio").apply();
        else prefs.edit().putString("audio", fileOrFolder.getPath()).apply();
    }

    /** The screensaver's own level, 10-100 %, separate from the TV's volume. */
    int volume() {
        return prefs.getInt("volume", 50);
    }

    void setVolume(int percent) {
        prefs.edit().putInt("volume", percent).apply();
    }

    /** The tracks to play, looping: the one file, or the folder's tracks by name. */
    List<File> tracks() {
        File a = audio();
        List<File> result = new ArrayList<>();
        if (a == null) return result;
        if (a.isFile()) {
            if (audioFile(a.getName())) result.add(a);
            return result;
        }
        return audioIn(a);
    }

    static boolean audioFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return !n.startsWith(".") && (n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".ogg")
                || n.endsWith(".opus") || n.endsWith(".wav") || n.endsWith(".flac"));
    }

    static List<File> audioIn(File folder) {
        List<File> result = new ArrayList<>();
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        for (File f : entries) if (f.isFile() && f.length() > 0 && audioFile(f.getName())) result.add(f);
        return result;
    }

    /** Folders with audio: Music, Download, Pictures/Screensaver and USB drives, two levels down. */
    static List<File> audioFolders() {
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        roots.add(defaultFolder());
        File[] volumes = new File("/storage").listFiles();
        if (volumes != null) {
            for (File v : volumes) {
                String n = v.getName();
                if (!n.equals("emulated") && !n.equals("self") && !n.startsWith("sdcard") && v.isDirectory()) roots.add(v);
            }
        }
        List<File> found = new ArrayList<>();
        for (File root : roots) collectAudio(root, 0, found);
        return found;
    }

    private static void collectAudio(File dir, int depth, List<File> found) {
        if (dir == null || !dir.isDirectory() || dir.getName().startsWith(".") || dir.getName().equals("Android")) return;
        if (!found.contains(dir) && !audioIn(dir).isEmpty()) found.add(dir);
        if (depth >= 2) return;
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs == null) return;
        Arrays.sort(subs, Comparator.comparing(File::getName));
        for (File s : subs) collectAudio(s, depth + 1, found);
    }

    /** Every photo in the folder, by name. */
    List<File> folderPhotos() {
        return images(folder());
    }

    /** What the screensaver shows: the picked photos, or the whole folder when none (or none still there) are picked. */
    List<File> photos() {
        List<File> all = folderPhotos();
        Set<String> picked = selected();
        if (picked.isEmpty()) return all;
        List<File> some = new ArrayList<>();
        for (File f : all) if (picked.contains(f.getName())) some.add(f);
        return some.isEmpty() ? all : some;
    }

    static boolean image(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return !n.startsWith(".") && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"));
    }

    static List<File> images(File folder) {
        List<File> result = new ArrayList<>();
        File[] entries = folder == null ? null : folder.listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        for (File f : entries) {
            if (f.isFile() && f.length() > 0 && f.length() <= 30L * 1024 * 1024 && image(f.getName())) result.add(f);
            if (result.size() == MAX_PHOTOS) break;
        }
        return result;
    }

    /**
     * Folders with photos to choose from: Pictures, DCIM and Download in the TV's storage and any USB drive, and
     * their subfolders two levels down. The default folder is always offered, even while empty.
     */
    static List<File> folders() {
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES));
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM));
        roots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        File[] volumes = new File("/storage").listFiles();
        if (volumes != null) {
            for (File v : volumes) {
                String n = v.getName();
                if (!n.equals("emulated") && !n.equals("self") && !n.startsWith("sdcard") && v.isDirectory()) roots.add(v);
            }
        }
        List<File> found = new ArrayList<>();
        found.add(defaultFolder());
        for (File root : roots) collect(root, 0, found);
        return found;
    }

    private static void collect(File dir, int depth, List<File> found) {
        if (dir == null || !dir.isDirectory() || dir.getName().startsWith(".") || dir.getName().equals("Android")) return;
        if (!found.contains(dir) && !images(dir).isEmpty()) found.add(dir);
        if (depth >= 2) return;
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs == null) return;
        Arrays.sort(subs, Comparator.comparing(File::getName));
        for (File s : subs) collect(s, depth + 1, found);
    }
}
