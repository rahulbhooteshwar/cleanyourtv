package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher settings > Wallpaper as a grid of thumbnails: Theme default and None first, then every picture the theme
 * carries and every one in Pictures/Wallpapers. Thumbnails are decoded small (about 320 px wide, RGB_565) on one
 * background thread and kept in a small cache, so a 1 GB TV scrolls the grid without decoding full pictures.
 */
final class WallpaperPicker {

    interface Listener {
        /** null: theme default; {@link WallpaperRotation#CHOICE_NONE}; or a picture's choice name. */
        void onPicked(String choice);
    }

    private static final int COLUMNS = 4;
    private static final int THUMB_WIDTH = 320;

    private final Activity activity;
    private final List<File> images;
    private final String current;
    private final Listener listener;
    private final float density;
    private final Typeface medium;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private Dialog dialog;

    static void show(Activity activity, List<File> images, String current, Listener listener) {
        new WallpaperPicker(activity, images, current, listener).open();
    }

    private WallpaperPicker(Activity activity, List<File> images, String current, Listener listener) {
        this.activity = activity;
        this.images = images;
        this.current = current;
        this.listener = listener;
        density = activity.getResources().getDisplayMetrics().density;
        medium = UiFont.get(activity, R.string.font_medium);
    }

    private void open() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.glass_panel);
        box.setPadding(dp(24), dp(18), dp(24), dp(12));

        TextView title = text(22, Color.WHITE);
        title.setTypeface(medium);
        title.setText(R.string.wallpaper_label);
        box.addView(title);
        TextView hint = text(13, 0xBFFFFFFF);
        hint.setText(activity.getString(R.string.wallpaper_picker_hint, images.size()));
        hint.setPadding(0, dp(2), 0, dp(10));
        box.addView(hint);

        RecyclerView grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, COLUMNS));
        grid.setClipToPadding(false);
        grid.setPadding(dp(4), dp(4), dp(4), dp(4));
        grid.setAdapter(new Adapter());
        grid.setItemAnimator(null);
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int w = activity.getResources().getDisplayMetrics().widthPixels, h = activity.getResources().getDisplayMetrics().heightPixels;
            window.setLayout((int) (w * 0.86f), (int) (h * 0.86f));
        }
        dialog.setOnDismissListener(d -> {
            decoder.shutdownNow();
            cache.evictAll();
        });
        dialog.show();
        // Start on the current pick: Theme default (0), None (1), or the picture.
        int start = current == null ? 0 : WallpaperRotation.CHOICE_NONE.equals(current) ? 1 : 2 + indexOf(current);
        grid.scrollToPosition(Math.max(0, start));
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(Math.max(0, start));
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private int indexOf(String choice) {
        for (int i = 0; i < images.size(); i++) {
            if (WallpaperRotation.choiceName(images.get(i)).equals(choice)) return i;
        }
        return -2;
    }

    private void pick(String choice) {
        dialog.dismiss();
        listener.onPicked(choice);
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(tile());
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            h.image.setImageDrawable(null);
            h.image.setTag(null);
            if (position < 2) {
                boolean themeDefault = position == 0;
                h.image.setBackgroundColor(themeDefault ? 0xFF2A2F3A : 0xFF101114);
                h.badge.setText(themeDefault ? "★" : "✕");
                h.badge.setVisibility(View.VISIBLE);
                h.label.setText(themeDefault ? R.string.wallpaper_theme_default : R.string.wallpaper_none);
                boolean selected = themeDefault ? current == null : WallpaperRotation.CHOICE_NONE.equals(current);
                h.check.setVisibility(selected ? View.VISIBLE : View.GONE);
                h.itemView.setOnClickListener(v -> pick(themeDefault ? null : WallpaperRotation.CHOICE_NONE));
                return;
            }
            File file = images.get(position - 2);
            String choice = WallpaperRotation.choiceName(file);
            h.badge.setVisibility(View.GONE);
            h.image.setBackgroundColor(0xFF1C1E24);
            h.label.setText(label(file));
            h.check.setVisibility(choice.equals(current) ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> pick(choice));
            String key = file.getPath();
            Bitmap cached = cache.get(key);
            if (cached != null) {
                h.image.setImageBitmap(cached);
                return;
            }
            h.image.setTag(key);
            ImageView target = h.image;
            try {
                decoder.execute(() -> {
                    Bitmap bmp = thumbnail(file);
                    if (bmp != null) cache.put(key, bmp);
                    main.post(() -> {
                        if (key.equals(target.getTag()) && bmp != null) target.setImageBitmap(bmp);
                    });
                });
            } catch (java.util.concurrent.RejectedExecutionException closed) {
                // the dialog is gone
            }
        }

        @Override
        public int getItemCount() {
            return images.size() + 2;
        }
    }

    /** "20-scenes-01-dusk-peaks.jpg" -> "scenes 01 dusk peaks"; a shared picture keeps its own name. */
    private static String label(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        if (WallpaperRotation.choiceName(file).startsWith(WallpaperRotation.SHARED_PREFIX)) return name;
        return name.replaceFirst("^\\d+-", "").replace('-', ' ').replace('_', ' ');
    }

    private static Bitmap thumbnail(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= THUMB_WIDTH) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView label, badge, check;

        Holder(View v) {
            super(v);
            image = v.findViewWithTag("image");
            label = v.findViewWithTag("label");
            badge = v.findViewWithTag("badge");
            check = v.findViewWithTag("check");
        }
    }

    private View tile() {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setPadding(dp(5), dp(5), dp(5), dp(4));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x33FFFFFF);
        focused.setStroke(dp(3), Color.WHITE);
        focused.setCornerRadius(dp(12));
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        tile.setBackground(bg);
        tile.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                .setDuration(120).start());

        FrameLayout frame = new FrameLayout(activity);
        ImageView image = new ImageView(activity);
        image.setTag("image");
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setClipToOutline(true);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(8));
        round.setColor(0xFF1C1E24);
        image.setBackground(round);
        frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));
        TextView badge = text(30, 0xCCFFFFFF);
        badge.setTag("badge");
        badge.setGravity(Gravity.CENTER);
        frame.addView(badge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)));
        TextView check = text(14, Color.WHITE);
        check.setTag("check");
        check.setText("✓");
        check.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF2E9E5B);
        check.setBackground(dot);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(6), dp(6), 0);
        frame.addView(check, clp);
        tile.addView(frame);

        TextView label = text(12, 0xE6FFFFFF);
        label.setTag("label");
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(2), dp(5), dp(2), 0);
        tile.addView(label);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        tile.setLayoutParams(lp);
        return tile;
    }

    private TextView text(float sp, int color) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
