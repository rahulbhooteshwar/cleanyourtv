package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings menu (hold OK) > Wallpaper: whether Home shows a picture, the folder to tick pictures in (Pictures/Wallpapers
 * until another is picked), which pictures (the theme's own as one "Theme default" tile, plus any number ticked in
 * the folder), and how often Home moves through them. Config reads the choices; Home rebuilds when they change.
 */
public class WallpaperSettingsActivity extends Activity {

    /** The picker's key for the "Theme default" tile. */
    private static final String THEME = "\u0000theme";
    private static final int[] ROTATIONS = {0, 3, 5, 900, 1800, 3600, 10800};

    private SharedPreferences choices;
    private float density;
    private TextView onRow, folderRow, picturesRow;
    private final List<TextView> rotationChoices = new ArrayList<>();
    private int themeSeconds;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        Config config = Config.load(this);   // also turns an older single wallpaper pick into these choices
        themeSeconds = config.wallpaperRotationSeconds;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF111419);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(72), dp(40), dp(72), dp(48));
        scroll.addView(list);

        TextView title = new TextView(this);
        title.setText(R.string.wallpaper_label);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        onRow = row(list, v -> {
            choices.edit().putBoolean(Config.WALLPAPER_OFF_KEY, !choices.getBoolean(Config.WALLPAPER_OFF_KEY, false)).apply();
            changed();
        });
        picturesRow = row(list, v -> pickPictures());
        folderRow = row(list, v -> pickFolder());

        label(list, R.string.wallpaper_every);
        LinearLayout rotations = new LinearLayout(this);
        list.addView(rotations, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int s : ROTATIONS) {
            String text = s == 0 ? getString(R.string.off) : s < 60 ? s + " s" : s < 3600 ? (s / 60) + " min" : (s / 3600) + " h";
            rotationChoices.add(choice(rotations, text, v -> {
                choices.edit().putInt(Config.ROTATION_SECONDS_KEY, s).remove(Config.ROTATION_KEY).apply();
                themeSeconds = s;
                changed();
            }));
        }
        TextView hint = new TextView(this);
        hint.setText(R.string.wallpaper_settings_hint);
        hint.setTextColor(0xB3FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setPadding(0, dp(18), 0, 0);
        list.addView(hint);

        setContentView(scroll);
        refresh();
        picturesRow.requestFocus();
    }

    private void refresh() {
        boolean off = choices.getBoolean(Config.WALLPAPER_OFF_KEY, false);
        onRow.setText(getString(R.string.wallpaper_on_state, getString(off ? R.string.off : R.string.on)));
        File folder = Config.wallpaperFolder(choices);
        folderRow.setText(getString(R.string.photo_dream_folder, folder.getPath().replace("/storage/emulated/0/", "")));
        boolean theme = choices.getBoolean(Config.WALLPAPER_THEME_KEY, true);
        int picked = ticked(folder).size();
        picturesRow.setText(theme && picked == 0 ? getString(R.string.wallpaper_pictures_theme)
                : theme ? getString(R.string.wallpaper_pictures_theme_plus, picked)
                : getString(R.string.wallpaper_pictures_some, picked));
        for (int i = 0; i < ROTATIONS.length; i++) rotationChoices.get(i).setSelected(ROTATIONS[i] == themeSeconds);
        for (View v : new View[]{picturesRow, folderRow}) v.setAlpha(off ? 0.5f : 1f);
    }

    /** The ticked names still in {@code folder}. */
    private Set<String> ticked(File folder) {
        Set<String> picked = choices.getStringSet(Config.WALLPAPER_PICKED_KEY, new HashSet<>());
        Set<String> present = new LinkedHashSet<>();
        for (File f : WallpaperRotation.images(folder)) if (picked.contains(f.getName())) present.add(f.getName());
        return present;
    }

    private void pickPictures() {
        File dir = Config.dir(this);
        File folder = Config.wallpaperFolder(choices);
        List<File> themePictures = new ArrayList<>();
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) themePictures.add(single);
        if (dir != null) themePictures.addAll(WallpaperRotation.images(new File(dir, ThemeFiles.WALLPAPERS)));
        List<File> pictures = WallpaperRotation.images(folder);

        List<PicturePicker.Item> items = new ArrayList<>();
        items.add(new PicturePicker.Item(THEME, getString(R.string.wallpaper_theme_tile, themePictures.size()),
                themePictures.isEmpty() ? null : themePictures.get(0), null, "★", getString(R.string.wallpaper_theme_note)));
        for (File f : pictures) items.add(new PicturePicker.Item(f.getName(), name(f), f, null));
        Set<String> ticked = new LinkedHashSet<>(ticked(folder));
        if (choices.getBoolean(Config.WALLPAPER_THEME_KEY, true)) ticked.add(THEME);
        PicturePicker.showMulti(this, getString(R.string.wallpaper_label),
                getString(R.string.wallpaper_pick_hint, folder.getPath().replace("/storage/emulated/0/", "")), items, ticked, keys -> {
                    boolean theme = keys.remove(THEME);
                    if (!theme && keys.isEmpty()) {
                        theme = true;   // nothing ticked: the theme's picture rather than a plain background
                        Toast.makeText(this, R.string.wallpaper_nothing_ticked, Toast.LENGTH_LONG).show();
                    }
                    choices.edit().putBoolean(Config.WALLPAPER_THEME_KEY, theme)
                            .putStringSet(Config.WALLPAPER_PICKED_KEY, new HashSet<>(keys)).apply();
                    changed();
                });
    }

    private void pickFolder() {
        List<File> folders = new ArrayList<>();
        folders.add(WallpaperRotation.shared());
        for (File f : PhotoSettings.folders()) if (!folders.contains(f)) folders.add(f);
        List<PicturePicker.Item> items = new ArrayList<>();
        for (File f : folders) {
            List<File> pictures = WallpaperRotation.images(f);
            items.add(new PicturePicker.Item(f.getPath(), f.getPath().replace("/storage/emulated/0/", "") + " (" + pictures.size() + ")",
                    pictures.isEmpty() ? null : pictures.get(0), "📁"));
        }
        PicturePicker.show(this, getString(R.string.photo_dream_pick_folder), getString(R.string.photo_dream_pick_folder_hint),
                items, Config.wallpaperFolder(choices).getPath(), key -> {
                    if (key == null || key.equals(Config.wallpaperFolder(choices).getPath())) return;
                    // Ticks belong to a folder: a new one starts with none (the theme's pictures stay as they were).
                    choices.edit().putString(Config.WALLPAPER_FOLDER_KEY, key).remove(Config.WALLPAPER_PICKED_KEY).apply();
                    changed();
                });
    }

    /** Home rebuilds with the new choices when it comes back (it compares this count). */
    private void changed() {
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        refresh();
    }

    /** "20-scenes-01-dusk-peaks.jpg" -> "20-scenes-01-dusk-peaks". */
    private static String name(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ---- views, as in PhotoDreamSettingsActivity ----

    private TextView row(LinearLayout parent, View.OnClickListener click) {
        TextView t = new TextView(this, null, 0, R.style.SettingsRow);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(12);
        t.setLayoutParams(lp);
        t.setOnClickListener(click);
        parent.addView(t);
        return t;
    }

    private void label(LinearLayout parent, int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setPadding(0, dp(24), 0, dp(10));
        parent.addView(t);
    }

    private TextView choice(LinearLayout row, String text, View.OnClickListener click) {
        TextView t = new TextView(this, null, 0, R.style.SettingsColumnChoice);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMarginEnd(dp(8));
        t.setLayoutParams(lp);
        t.setGravity(Gravity.CENTER);
        t.setText(text);
        t.setOnClickListener(click);
        row.addView(t);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
