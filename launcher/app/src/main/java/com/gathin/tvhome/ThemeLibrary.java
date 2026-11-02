package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Themes kept on the TV to pick from in Launcher settings: Config.dir/themes/<name>/ with a config.json, optionally a
 * wallpaper.jpg, a wallpapers/ collection and a preview.jpg (installed through ThemeProvider by
 * my-tv/install-themes.ps1). Applying one copies it over the active theme files, exactly what tools/theme.sh pushes,
 * and writes a new theme_revision so Config drops the on-TV choices the way it does after a push.
 */
final class ThemeLibrary {

    /** The name of the library theme last applied on the TV (shown in Launcher settings); cleared by a PC push only in name. */
    static final String CURRENT_KEY = "library_theme";

    static final class Theme {
        final String name;
        final File folder;

        Theme(String name, File folder) {
            this.name = name;
            this.folder = folder;
        }

        String label() {
            return labelOf(name);
        }

        /** preview.jpg, else the wallpaper, else the collection's first picture; null for none. */
        File preview() {
            File p = new File(folder, ThemeFiles.PREVIEW);
            if (p.isFile()) return p;
            File w = new File(folder, Config.WALLPAPER_NAME);
            if (w.isFile()) return w;
            List<File> c = WallpaperRotation.images(new File(folder, ThemeFiles.WALLPAPERS));
            return c.isEmpty() ? null : c.get(0);
        }
    }

    /** "nature-calm" -> "Nature calm"; {@link #MINE} -> "My theme". */
    static String labelOf(String name) {
        if (MINE.equals(name)) return "My theme";
        String s = name.replace('-', ' ').replace('_', ' ');
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private ThemeLibrary() {
    }

    static List<Theme> list(Context context) {
        List<Theme> themes = new ArrayList<>();
        File dir = Config.dir(context);
        File[] folders = dir == null ? null : new File(dir, ThemeFiles.LIBRARY).listFiles(File::isDirectory);
        if (folders == null) return themes;
        Arrays.sort(folders, Comparator.comparing(File::getName));
        for (File f : folders) {
            if (ThemeFiles.themeName(f.getName()) && new File(f, Config.FILE_NAME).isFile()) themes.add(new Theme(f.getName(), f));
        }
        return themes;
    }

    /** The library entry that keeps the theme the TV had before the picker was first used (pushed from the PC). */
    static final String MINE = "my-theme";

    /**
     * Before the first theme from the picker replaces it, keep the active theme (config.json, wallpaper and collection)
     * as {@link #MINE}, so it can be picked again. Runs on a worker thread.
     */
    static void keepCurrent(Context context) {
        File dir = Config.dir(context);
        if (dir == null) return;
        File config = new File(dir, Config.FILE_NAME), target = new File(new File(dir, ThemeFiles.LIBRARY), MINE);
        if (!config.isFile() || new File(target, Config.FILE_NAME).isFile()) return;
        try {
            File collection = new File(target, ThemeFiles.WALLPAPERS);
            //noinspection ResultOfMethodCallIgnored
            collection.mkdirs();
            File wallpaper = new File(dir, Config.WALLPAPER_NAME);
            if (wallpaper.isFile()) copy(wallpaper, new File(target, Config.WALLPAPER_NAME));
            File[] pictures = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name)
                    || WallpaperCaptions.FILE_NAME.equals(name));
            if (pictures != null) for (File f : pictures) copy(f, new File(collection, f.getName()));
            copy(config, new File(target, Config.FILE_NAME));   // last: the entry counts once its config.json is there
        } catch (IOException ignored) {
            // the picker still works; the old theme just isn't kept
        }
    }

    /** Copies {@code theme} over the active theme files. Runs on a worker thread; returns false if a copy failed. */
    static boolean apply(Context context, Theme theme) {
        File dir = Config.dir(context);
        if (dir == null) return false;
        try {
            copy(new File(theme.folder, Config.FILE_NAME), new File(dir, Config.FILE_NAME));
            File wallpaper = new File(theme.folder, Config.WALLPAPER_NAME), active = new File(dir, Config.WALLPAPER_NAME);
            if (wallpaper.isFile()) copy(wallpaper, active);
            else //noinspection ResultOfMethodCallIgnored
                active.delete();
            File collection = new File(dir, ThemeFiles.WALLPAPERS);
            File[] old = collection.listFiles((d, name) -> ThemeFiles.collectionImage(name)
                    || WallpaperCaptions.FILE_NAME.equals(name));
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            File[] pictures = new File(theme.folder, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name)
                    || WallpaperCaptions.FILE_NAME.equals(name));
            if (pictures != null) {
                //noinspection ResultOfMethodCallIgnored
                collection.mkdirs();
                for (File f : pictures) copy(f, new File(collection, f.getName()));
            }
            // A new revision token: Config sees a changed theme and resets columns, wallpaper pick and the rest.
            try (OutputStream out = new FileOutputStream(new File(dir, "theme_revision"))) {
                out.write(("library:" + theme.name + ":" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            }
            context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(CURRENT_KEY, theme.name).apply();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copy(File from, File to) throws IOException {
        File tmp = new File(to.getPath() + ".tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
        }
        if (!tmp.renameTo(to)) {
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            if (!tmp.renameTo(to)) throw new IOException("rename " + to);
        }
    }
}
