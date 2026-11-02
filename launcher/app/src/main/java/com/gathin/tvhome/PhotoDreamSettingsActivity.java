package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The built-in photo screensaver's settings (Screensaver menu, or the TV's own screensaver settings where it has
 * them): folder, which photos, how often they change, clock and calendar with their place, background audio and its
 * level, a preview, and making it the screensaver. Built in code with Launcher settings' row styles.
 */
public class PhotoDreamSettingsActivity extends Activity {

    private PhotoSettings settings;
    private float density;
    private TextView folderRow, photosRow, clockRow, calendarRow, audioRow, useRow;
    private final List<TextView> secondChoices = new ArrayList<>(), positionChoices = new ArrayList<>(), volumeChoices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new PhotoSettings(this);
        density = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF111419);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(72), dp(40), dp(72), dp(48));
        scroll.addView(list);

        TextView title = new TextView(this);
        title.setText(R.string.photo_dream_settings);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        folderRow = row(list, v -> pickFolder());
        photosRow = row(list, v -> pickPhotos());

        label(list, R.string.photo_dream_every);
        LinearLayout seconds = choices(list);
        for (int s : PhotoSettings.SECONDS) {
            secondChoices.add(choice(seconds, s < 60 ? s + " s" : (s / 60) + " min", v -> {
                settings.setSeconds(s);
                refresh();
            }));
        }

        clockRow = row(list, v -> {
            settings.setClock(!settings.clock());
            refresh();
        });
        calendarRow = row(list, v -> {
            settings.setCalendar(!settings.calendar());
            refresh();
        });
        label(list, R.string.photo_dream_position);
        LinearLayout positions = choices(list);
        int[] names = {R.string.position_tl, R.string.position_tr, R.string.position_c, R.string.position_bl, R.string.position_br};
        for (int i = 0; i < PhotoSettings.POSITIONS.length; i++) {
            String p = PhotoSettings.POSITIONS[i];
            positionChoices.add(choice(positions, getString(names[i]), v -> {
                settings.setPosition(p);
                refresh();
            }));
        }

        audioRow = row(list, v -> pickAudio());
        label(list, R.string.photo_dream_volume);
        LinearLayout volumes = choices(list);
        for (int vol : PhotoSettings.VOLUMES) {
            volumeChoices.add(choice(volumes, vol + "%", v -> {
                settings.setVolume(vol);
                refresh();
            }));
        }

        TextView preview = row(list, v -> startActivity(new Intent(this, PhotoShowActivity.class)));
        preview.setText(R.string.photo_dream_preview);
        ((LinearLayout.LayoutParams) preview.getLayoutParams()).topMargin = dp(28);
        useRow = row(list, v -> useAsScreensaver());

        setContentView(scroll);
        refresh();
        folderRow.requestFocus();
    }

    private void refresh() {
        File folder = settings.folder();
        int all = settings.folderPhotos().size();
        folderRow.setText(getString(R.string.photo_dream_folder, folder.getPath().replace("/storage/emulated/0/", "")));
        Set<String> picked = settings.selected();
        int chosen = settings.photos().size();
        photosRow.setText(picked.isEmpty() || chosen == all ? getString(R.string.photo_dream_photos_all, all)
                : getString(R.string.photo_dream_photos_some, chosen, all));
        for (int i = 0; i < secondChoices.size(); i++) secondChoices.get(i).setSelected(PhotoSettings.SECONDS[i] == settings.seconds());
        clockRow.setText(getString(R.string.photo_dream_clock, getString(settings.clock() ? R.string.on : R.string.off)));
        calendarRow.setText(getString(R.string.photo_dream_calendar, getString(settings.calendar() ? R.string.on : R.string.off)));
        for (int i = 0; i < positionChoices.size(); i++) {
            positionChoices.get(i).setSelected(PhotoSettings.POSITIONS[i].equals(settings.position()));
        }
        File audio = settings.audio();
        audioRow.setText(getString(R.string.photo_dream_audio, audio == null ? getString(R.string.off)
                : audio.isFile() ? audio.getName() : audio.getName() + " (" + settings.tracks().size() + ")"));
        for (int i = 0; i < volumeChoices.size(); i++) {
            volumeChoices.get(i).setSelected(PhotoSettings.VOLUMES[i] == settings.volume());
            volumeChoices.get(i).setEnabled(audio != null);
            volumeChoices.get(i).setAlpha(audio != null ? 1f : 0.5f);
        }
        useRow.setText(isScreensaver() ? R.string.photo_dream_is_current : R.string.photo_dream_use);
    }

    private void pickFolder() {
        List<PicturePicker.Item> items = new ArrayList<>();
        for (File f : PhotoSettings.folders()) {
            List<File> photos = PhotoSettings.images(f);
            String name = f.getPath().replace("/storage/emulated/0/", "");
            items.add(new PicturePicker.Item(f.getPath(), name + " (" + photos.size() + ")",
                    photos.isEmpty() ? null : photos.get(0), "📁"));
        }
        PicturePicker.show(this, getString(R.string.photo_dream_pick_folder), getString(R.string.photo_dream_pick_folder_hint),
                items, settings.folder().getPath(), key -> {
                    if (key != null) settings.setFolder(new File(key));
                    refresh();
                });
    }

    private void pickPhotos() {
        List<File> photos = settings.folderPhotos();
        if (photos.isEmpty()) {
            Toast.makeText(this, getString(R.string.photo_dream_empty, settings.folder().getPath()), Toast.LENGTH_LONG).show();
            return;
        }
        List<PicturePicker.Item> items = new ArrayList<>();
        for (File f : photos) items.add(new PicturePicker.Item(f.getName(), f.getName(), f, null));
        Set<String> ticked = settings.selected();
        if (ticked.isEmpty()) for (File f : photos) ticked.add(f.getName());
        PicturePicker.showMulti(this, getString(R.string.photo_dream_pick_photos), getString(R.string.photo_dream_pick_photos_hint),
                items, ticked, keys -> {
                    // All ticked (or none) means the whole folder, which also takes in photos added later.
                    settings.setSelected(keys.size() == photos.size() ? new HashSet<>() : keys);
                    refresh();
                });
    }

    private void pickAudio() {
        List<PicturePicker.Item> items = new ArrayList<>();
        items.add(new PicturePicker.Item("", getString(R.string.off), null, "✕"));
        int tracks = 0;
        for (File folder : PhotoSettings.audioFolders()) {
            List<File> inside = PhotoSettings.audioIn(folder);
            items.add(new PicturePicker.Item(folder.getPath(), folder.getName() + " (" + inside.size() + ")", null, "📁"));
            for (File t : inside) {
                if (tracks++ >= 120) break;
                items.add(new PicturePicker.Item(t.getPath(), t.getName(), null, "♪"));
            }
        }
        File current = settings.audio();
        PicturePicker.show(this, getString(R.string.photo_dream_pick_audio),
                getString(items.size() > 1 ? R.string.photo_dream_pick_audio_hint : R.string.photo_dream_no_audio),
                items, current == null ? "" : current.getPath(), key -> {
                    settings.setAudio(key == null || key.isEmpty() ? null : new File(key));
                    refresh();
                });
    }

    private boolean isScreensaver() {
        String current = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        return current != null && current.split(",")[0].contains(getPackageName() + "/" + PhotoDream.class.getName())
                || current != null && current.split(",")[0].equals(new ComponentName(this, PhotoDream.class).flattenToShortString());
    }

    private void useAsScreensaver() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.screensaver_no_permission, Toast.LENGTH_LONG).show();
            return;
        }
        Settings.Secure.putString(getContentResolver(), "screensaver_components",
                new ComponentName(this, PhotoDream.class).flattenToShortString());
        Settings.Secure.putInt(getContentResolver(), "screensaver_enabled", 1);
        Toast.makeText(this, getString(R.string.screensaver_set, getString(R.string.photo_dream_name)), Toast.LENGTH_SHORT).show();
        refresh();
    }

    // ---- views ----

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

    private LinearLayout choices(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView choice(LinearLayout row, String text, View.OnClickListener click) {
        TextView t = new TextView(this, null, 0, R.style.SettingsColumnChoice);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1);
        lp.setMarginEnd(dp(10));
        t.setLayoutParams(lp);
        t.setGravity(android.view.Gravity.CENTER);
        t.setText(text);
        t.setOnClickListener(click);
        row.addView(t);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
