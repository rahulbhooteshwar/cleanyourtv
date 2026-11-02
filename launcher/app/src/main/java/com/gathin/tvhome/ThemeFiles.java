package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        // The theme library (ThemeLibrary): themes/<name>/config.json, wallpaper.jpg, preview.jpg and
        // themes/<name>/wallpapers/<image>, installed by my-tv/install-themes.ps1 and applied on the TV.
        if (segments.size() >= 3 && LIBRARY.equals(segments.get(0)) && themeName(segments.get(1))) {
            String base = LIBRARY + "/" + segments.get(1) + "/";
            if (segments.size() == 3) {
                String name = segments.get(2);
                return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || PREVIEW.equals(name) ? base + name : null;
            }
            if (segments.size() == 4 && WALLPAPERS.equals(segments.get(2))
                    && (collectionImage(segments.get(3)) || WallpaperCaptions.FILE_NAME.equals(segments.get(3)))) {
                return base + WALLPAPERS + "/" + segments.get(3);
            }
        }
        return null;
    }

    static final String LIBRARY = "themes";
    static final String PREVIEW = "preview.jpg";
    private static final Pattern THEME_NAME = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    static boolean themeName(String name) {
        return name != null && THEME_NAME.matcher(name).matches();
    }

    /** "themes/<name>/wallpapers": a delete of it clears that library theme's collection. */
    static String libraryCollection(List<String> segments) {
        if (segments == null || segments.size() != 3 || !LIBRARY.equals(segments.get(0))
                || !themeName(segments.get(1)) || !WALLPAPERS.equals(segments.get(2))) return null;
        return LIBRARY + "/" + segments.get(1) + "/" + WALLPAPERS;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
