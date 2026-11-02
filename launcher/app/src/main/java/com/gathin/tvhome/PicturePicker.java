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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A grid of picture tiles to pick one from, for Launcher settings' Wallpaper and Theme rows. Thumbnails are decoded
 * small (about 320 px wide, RGB_565) on one background thread, only for the tiles that scroll into view, and kept in
 * a small cache, so a 1 GB TV scrolls the grid without decoding full pictures. A picture can also be on the web
 * (an online theme's screenshot): it is downloaded once into the app's cache when its tile first shows. A tile with
 * neither shows a glyph (Theme default, None).
 */
final class PicturePicker {

    static final class Item {
        /** What {@link Listener#onPicked} receives; may be null. */
        final String key;
        final String label;
        final File picture;
        final String pictureUrl;
        final String glyph;
        /** A small line under the label, e.g. "Online · install". */
        final String note;

        Item(String key, String label, File picture, String glyph) {
            this(key, label, picture, null, glyph, null);
        }

        Item(String key, String label, File picture, String pictureUrl, String glyph, String note) {
            this.key = key;
            this.label = label;
            this.picture = picture;
            this.pictureUrl = pictureUrl;
            this.glyph = glyph;
            this.note = note;
        }
    }

    interface Listener {
        void onPicked(String key);
    }

    private static final int COLUMNS = 4;
    private static final int THUMB_WIDTH = 320;

    private final Activity activity;
    private final List<Item> items;
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

    /** Opens the grid with the item whose key equals {@code current} marked and focused. */
    static void show(Activity activity, String title, String hint, List<Item> items, String current, Listener listener) {
        new PicturePicker(activity, items, current, listener).open(title, hint);
    }

    private PicturePicker(Activity activity, List<Item> items, String current, Listener listener) {
        this.activity = activity;
        this.items = items;
        this.current = current;
        this.listener = listener;
        density = activity.getResources().getDisplayMetrics().density;
        medium = UiFont.get(activity, R.string.font_medium);
    }

    private void open(String titleText, String hintText) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.glass_panel);
        box.setPadding(dp(24), dp(18), dp(24), dp(12));

        TextView title = text(22, Color.WHITE);
        title.setTypeface(medium);
        title.setText(titleText);
        box.addView(title);
        TextView hint = text(13, 0xBFFFFFFF);
        hint.setText(hintText);
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
        int start = 0;
        for (int i = 0; i < items.size(); i++) if (Objects.equals(items.get(i).key, current)) start = i;
        final int focus = start;
        grid.scrollToPosition(focus);
        grid.post(() -> {
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(focus);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    private void pick(String key) {
        dialog.dismiss();
        listener.onPicked(key);
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(tile());
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item item = items.get(position);
            h.image.setImageDrawable(null);
            h.image.setTag(null);
            h.label.setText(item.label);
            h.note.setText(item.note == null ? "" : item.note);
            h.note.setVisibility(item.note == null ? View.GONE : View.VISIBLE);
            h.check.setVisibility(Objects.equals(item.key, current) ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> pick(item.key));
            boolean glyph = item.picture == null && item.pictureUrl == null;
            h.badge.setVisibility(glyph ? View.VISIBLE : View.GONE);
            h.badge.setText(item.glyph == null ? "" : item.glyph);
            if (glyph) return;
            String key = item.picture != null ? item.picture.getPath() : item.pictureUrl;
            Bitmap cached = cache.get(key);
            if (cached != null) {
                h.image.setImageBitmap(cached);
                return;
            }
            h.image.setTag(key);
            ImageView target = h.image;
            try {
                decoder.execute(() -> {
                    File file = item.picture != null ? item.picture : WebFiles.cached(activity, item.pictureUrl);
                    Bitmap bmp = file == null ? null : thumbnail(file);
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
            return items.size();
        }
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
        final TextView label, note, badge, check;

        Holder(View v) {
            super(v);
            image = v.findViewWithTag("image");
            label = v.findViewWithTag("label");
            note = v.findViewWithTag("note");
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
        TextView note = text(10, 0xFFFFB23C);
        note.setTag("note");
        note.setSingleLine(true);
        note.setPadding(dp(2), dp(1), dp(2), 0);
        tile.addView(note);
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
