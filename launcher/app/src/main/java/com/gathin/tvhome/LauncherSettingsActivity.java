package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView themeChoice;
    private TextView glowChoice;
    private TextView rotationChoice;
    /** Wallpaper rotation steps in minutes; 0 is off. */
    private static final int[] ROTATIONS = {0, 15, 30, 60, 180};
    private int rotationMinutes;
    /** The app colour glow behind the wallpaper (ambient_glow), as shown on Home now. */
    private boolean glowOn;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        rotationChoice = findViewById(R.id.rotationChoice);
        rotationMinutes = config.wallpaperRotationMinutes;
        rotationChoice.setOnClickListener(v -> {
            int i = 0;
            while (i < ROTATIONS.length && ROTATIONS[i] != rotationMinutes) i++;
            rotationMinutes = ROTATIONS[(i + 1) % ROTATIONS.length];
            SharedPreferences.Editor e = choices.edit().putInt(Config.ROTATION_KEY, rotationMinutes);
            // A picked wallpaper holds rotation still; turning rotation on means letting it run.
            if (rotationMinutes > 0) e.remove(Config.WALLPAPER_CHOICE_KEY);
            e.apply();
            changed();
            refresh();
        });
        glowChoice = findViewById(R.id.glowChoice);
        // config.ambientGlow already has this setting's choice applied over the theme's.
        glowOn = config.ambientGlow;
        glowChoice.setOnClickListener(v -> {
            boolean on = glowOn = !glowOn;
            choices.edit().putBoolean(Config.AMBIENT_GLOW_KEY, on).apply();
            changed();
            refresh();
        });
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        // The theme's collection and Pictures/Wallpapers (a file manager fills that one).
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.pickable(dir));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        List<PicturePicker.Item> items = new java.util.ArrayList<>();
        items.add(new PicturePicker.Item(null, getString(R.string.wallpaper_theme_default), null, "★"));
        items.add(new PicturePicker.Item(WallpaperRotation.CHOICE_NONE, getString(R.string.wallpaper_none), null, "✕"));
        for (File f : images) items.add(new PicturePicker.Item(WallpaperRotation.choiceName(f), wallpaperLabel(f), f, null));
        PicturePicker.show(this, getString(R.string.wallpaper_label),
                getString(R.string.wallpaper_picker_hint, images.size()), items, current, choice -> {
            if (choice == null) choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
            else choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, choice).apply();
            changed();
            if (choice != null) Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
        });
    }

    /** "20-scenes-01-dusk-peaks.jpg" -> "scenes 01 dusk peaks"; a Pictures/Wallpapers picture keeps its own name. */
    private static String wallpaperLabel(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (WallpaperRotation.choiceName(file).startsWith(WallpaperRotation.SHARED_PREFIX)) return name;
        return name.replaceFirst("^\\d+-", "").replace('-', ' ').replace('_', ' ');
    }

    /**
     * Launcher settings > Theme: the themes kept on the TV, then the original CleanYourTV repository's themes that
     * aren't installed yet. Picking one applies it (installing it first), and Home rebuilds with it.
     */
    private void showThemePicker() {
        Toast loading = Toast.makeText(this, R.string.theme_loading, Toast.LENGTH_SHORT);
        loading.show();
        new Thread(() -> {
            ThemeLibrary.keepCurrent(this);
            List<ThemeLibrary.Theme> installed = ThemeLibrary.list(this);
            List<ThemeGallery.Online> online = ThemeGallery.load(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                loading.cancel();
                java.util.Set<String> have = new java.util.HashSet<>();
                List<PicturePicker.Item> items = new java.util.ArrayList<>();
                for (ThemeLibrary.Theme t : installed) {
                    have.add(t.name);
                    items.add(new PicturePicker.Item("lib:" + t.name, t.label(), t.preview(), null, "▣", null));
                }
                int fresh = 0;
                for (ThemeGallery.Online o : online) {
                    if (have.contains(o.name)) continue;
                    fresh++;
                    items.add(new PicturePicker.Item("web:" + o.name, ThemeLibrary.labelOf(o.name), null, o.previewUrl(),
                            "⬇", getString(R.string.theme_online)));
                }
                String hint = online.isEmpty() ? getString(R.string.theme_hint_offline, installed.size())
                        : getString(R.string.theme_hint, installed.size(), fresh);
                String current = choices.getString(ThemeLibrary.CURRENT_KEY, null);
                PicturePicker.show(this, getString(R.string.theme_label), hint, items,
                        current == null ? null : "lib:" + current, this::pickTheme);
            });
        }).start();
    }

    private void pickTheme(String key) {
        if (key == null) return;
        boolean web = key.startsWith("web:");
        String name = key.substring(4);
        Toast.makeText(this, web ? R.string.theme_installing : R.string.theme_applying, Toast.LENGTH_LONG).show();
        new Thread(() -> {
            boolean ok = true;
            if (web) {
                ok = false;
                for (ThemeGallery.Online o : ThemeGallery.load(this)) {
                    if (o.name.equals(name)) ok = ThemeGallery.install(this, o);
                }
            }
            ThemeLibrary.Theme theme = null;
            if (ok) for (ThemeLibrary.Theme t : ThemeLibrary.list(this)) if (t.name.equals(name)) theme = t;
            boolean applied = theme != null && ThemeLibrary.apply(this, theme);
            runOnUiThread(() -> {
                if (!applied) {
                    Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                changed();
                Toast.makeText(this, getString(R.string.theme_applied, ThemeLibrary.labelOf(name)), Toast.LENGTH_SHORT).show();
                finish();   // back to Home, which rebuilds with the new theme
            });
        }).start();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        glowChoice.setText(getString(R.string.glow_state, getString(glowOn ? R.string.on : R.string.off)));
        boolean picked = choices.contains(Config.WALLPAPER_CHOICE_KEY);
        rotationChoice.setText(getString(R.string.rotation_state, rotationMinutes <= 0 || picked ? getString(R.string.off)
                : rotationMinutes < 60 ? rotationMinutes + " min" : (rotationMinutes / 60) + " h"));
        String theme = choices.getString(ThemeLibrary.CURRENT_KEY, null);
        themeChoice.setText(getString(R.string.theme_state, theme == null ? getString(R.string.theme_custom) : ThemeLibrary.labelOf(theme)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
