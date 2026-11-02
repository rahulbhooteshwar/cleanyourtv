package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The built-in photo screensaver's picture: the chosen photos crossfading with a slow zoom (one photo just stays),
 * and, as chosen, a clock and a small month calendar with today's tithi in one of the four corners or the middle.
 * Photos are decoded at screen size on a worker thread, turned upright from their EXIF orientation; the corner block
 * moves a few pixels every minute so nothing burns into the panel. Shared by PhotoDream and its preview.
 */
final class PhotoShow extends FrameLayout {

    private static final int FADE_MS = 1500;

    private final PhotoSettings settings;
    private final ImageView[] views = new ImageView[2];
    private final LinearLayout corner;
    private final TextClock clock;
    private final TextView date;
    private final LinearLayout month;
    private final TextView message;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final float density;
    private final int screenW, screenH;
    private ExecutorService decoder;
    private List<File> photos;
    private int front, index = -1;
    private boolean running;
    private LocalDate drawnDay;
    private final Runnable advance = this::next;
    private final Runnable minute = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (!LocalDate.now(Panchang.ZONE).equals(drawnDay)) drawCalendar();
            corner.animate().translationX(dp(random.nextInt(25) - 12)).translationY(dp(random.nextInt(25) - 12))
                    .setDuration(2000).start();
            main.postDelayed(this, 60_000);
        }
    };

    PhotoShow(Context context) {
        super(context);
        settings = new PhotoSettings(context);
        density = context.getResources().getDisplayMetrics().density;
        screenW = context.getResources().getDisplayMetrics().widthPixels;
        screenH = context.getResources().getDisplayMetrics().heightPixels;
        setBackgroundColor(Color.BLACK);
        for (int i = 0; i < 2; i++) {
            ImageView v = new ImageView(context);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            addView(v, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            views[i] = v;
        }
        message = text(20, 0xCCFFFFFF, false);
        message.setGravity(Gravity.CENTER);
        message.setVisibility(GONE);
        addView(message, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        corner = new LinearLayout(context);
        corner.setOrientation(LinearLayout.VERTICAL);
        corner.setPadding(dp(22), dp(16), dp(22), dp(16));
        GradientDrawable card = new GradientDrawable();
        card.setColor(0x73000000);
        card.setCornerRadius(dp(18));
        corner.setBackground(card);
        clock = new TextClock(context);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(UiFont.get(context, R.string.font_light));
        clock.setIncludeFontPadding(false);
        date = text(16, 0xE6FFFFFF, true);
        month = new LinearLayout(context);
        month.setOrientation(LinearLayout.VERTICAL);
        corner.addView(clock);
        corner.addView(date);
        corner.addView(month);
        addView(corner, cornerParams());
    }

    void start() {
        if (running) return;
        running = true;
        decoder = Executors.newSingleThreadExecutor();
        boolean showClock = settings.clock(), showCalendar = settings.calendar();
        clock.setVisibility(showClock ? VISIBLE : GONE);
        date.setVisibility(showClock || showCalendar ? VISIBLE : GONE);
        month.setVisibility(showCalendar ? VISIBLE : GONE);
        corner.setVisibility(showClock || showCalendar ? VISIBLE : GONE);
        ClockFormat.apply(this, Config.load(getContext()).clockFormat);
        drawCalendar();
        main.postDelayed(minute, 60_000);
        startAudio();
        photos = settings.photos();
        if (photos.isEmpty()) {
            message.setText(getContext().getString(R.string.photo_dream_empty, settings.folder().getPath()));
            message.setVisibility(VISIBLE);
            return;
        }
        next();
    }

    void stop() {
        running = false;
        main.removeCallbacks(advance);
        main.removeCallbacks(minute);
        if (decoder != null) decoder.shutdownNow();
        stopAudio();
    }

    // ---- background audio: the chosen track, or a folder's tracks in turn, looping, at the screensaver's own level ----

    private android.media.MediaPlayer player;
    private List<File> tracks;
    private int track = -1;

    private void startAudio() {
        tracks = settings.tracks();
        if (!tracks.isEmpty()) playNext();
    }

    private void playNext() {
        if (!running || tracks.isEmpty()) return;
        releasePlayer();
        track = (track + 1) % tracks.size();
        float level = settings.volume() / 100f;
        android.media.MediaPlayer p = new android.media.MediaPlayer();
        player = p;
        try {
            p.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setDataSource(tracks.get(track).getPath());
            p.setVolume(level * level, level * level);   // squared: the slider then sounds even
            p.setLooping(tracks.size() == 1);
            p.setOnPreparedListener(mp -> {
                if (running && mp == player) mp.start();
            });
            p.setOnCompletionListener(mp -> main.post(this::playNext));
            p.setOnErrorListener((mp, what, extra) -> {
                // An unplayable file: try the next one, unless it was the only one.
                if (tracks.size() > 1) main.postDelayed(this::playNext, 500);
                return true;
            });
            p.prepareAsync();
        } catch (Exception e) {
            if (tracks.size() > 1) main.postDelayed(this::playNext, 500);
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        try {
            player.release();
        } catch (Exception ignored) {
            // already released
        }
        player = null;
    }

    private void stopAudio() {
        main.removeCallbacksAndMessages(null);
        releasePlayer();
    }

    private void next() {
        if (!running || photos.isEmpty()) return;
        index = (index + 1) % photos.size();
        File file = photos.get(index);
        try {
            decoder.execute(() -> {
                Bitmap bmp = decode(file);
                main.post(() -> show(bmp));
            });
        } catch (java.util.concurrent.RejectedExecutionException stopped) {
            // the screensaver ended
        }
    }

    private void show(Bitmap bmp) {
        if (!running) return;
        long interval = settings.seconds() * 1000L;
        if (bmp == null) {
            // An unreadable file: skip it quickly, or show the message when it was the only one.
            if (photos.size() > 1) main.postDelayed(advance, 500);
            else {
                message.setText(getContext().getString(R.string.photo_dream_empty, settings.folder().getPath()));
                message.setVisibility(VISIBLE);
            }
            return;
        }
        ImageView in = views[1 - front], out = views[front];
        in.setImageBitmap(bmp);
        in.setScaleX(1f);
        in.setScaleY(1f);
        // A third of the interval at most, so a 3-second slideshow isn't half crossfade.
        long fade = Math.min(FADE_MS, interval / 3);
        in.animate().alpha(1f).setDuration(fade).start();
        out.animate().alpha(0f).setDuration(fade).withEndAction(() -> {
            if (out != views[front]) out.setImageDrawable(null);
        }).start();
        if (photos.size() > 1) {
            ObjectAnimator zoom = ObjectAnimator.ofPropertyValuesHolder(in,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f));
            zoom.setDuration(interval + fade);
            zoom.setInterpolator(new LinearInterpolator());
            zoom.start();
            main.postDelayed(advance, interval);
        }
        front = 1 - front;
    }

    /** At screen size or a little above, upright. */
    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0) return null;
            int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            boolean sideways = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270;
            int w = sideways ? bounds.outHeight : bounds.outWidth, h = sideways ? bounds.outWidth : bounds.outHeight;
            int sample = 1;
            while (w / (sample * 2) >= screenW && h / (sample * 2) >= screenH) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(file.getPath(), opts);
            if (bmp == null) return null;
            int degrees = orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            if (degrees == 0) return bmp;
            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap upright = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (upright != bmp) bmp.recycle();
            return upright;
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }

    /** The date line, and the month grid with today ringed and today's tithi in Hindi under it. */
    private void drawCalendar() {
        LocalDate today = LocalDate.now(Panchang.ZONE);
        drawnDay = today;
        Panchang.Day day = Panchang.month(today.getYear(), today.getMonthValue()).get(today.getDayOfMonth() - 1);
        String weekday = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        date.setText(weekday + ", " + today.getDayOfMonth() + " "
                + today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "\n" + day.longHi());
        month.removeAllViews();
        TextView title = text(13, 0xFFFFB23C, true);
        title.setText(today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + today.getYear());
        title.setPadding(0, dp(10), 0, dp(4));
        month.addView(title);
        String[] letters = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout head = new LinearLayout(getContext());
        for (int i = 0; i < 7; i++) head.addView(cell(letters[i], i == 0 ? 0xFFFF6B5E : 0x99FFFFFF, false));
        month.addView(head);
        int lead = today.withDayOfMonth(1).getDayOfWeek().getValue() % 7, length = today.lengthOfMonth();
        LinearLayout row = null;
        for (int i = 0; i < lead + length; i++) {
            if (i % 7 == 0) {
                row = new LinearLayout(getContext());
                month.addView(row);
            }
            int d = i - lead + 1;
            row.addView(d < 1 ? cell("", 0, false) : cell(String.valueOf(d), i % 7 == 0 ? 0xFFFF6B5E : 0xE6FFFFFF, d == today.getDayOfMonth()));
        }
    }

    private TextView cell(String s, int color, boolean today) {
        TextView t = text(11, today ? Color.BLACK : color, today);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        if (today) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0xFFFFB23C);
            t.setBackground(ring);
        }
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(20)));
        return t;
    }

    private LayoutParams cornerParams() {
        String p = settings.position();
        int gravity;
        switch (p) {
            case "tl": gravity = Gravity.TOP | Gravity.START; break;
            case "tr": gravity = Gravity.TOP | Gravity.END; break;
            case "bl": gravity = Gravity.BOTTOM | Gravity.START; break;
            case "c": gravity = Gravity.CENTER; break;
            default: gravity = Gravity.BOTTOM | Gravity.END;
        }
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        int m = dp(48);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private TextView text(float sp, int color, boolean medium) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(UiFont.get(getContext(), medium ? R.string.font_medium : R.string.font_regular));
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
