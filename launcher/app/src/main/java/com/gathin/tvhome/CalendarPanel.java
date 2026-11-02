package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Calendar pill's popup: one Gregorian month with each day's Vikram Samvat tithi and Indian holidays flagged
 * (see {@link Panchang}). Arrows in the header step a month or a year; Channel up/down, Page up/down and the
 * fast-forward/rewind keys step a month from anywhere, and Left on the 1st or Right on the last day walks into the
 * neighbouring month. A line under the grid spells out the focused day.
 */
final class CalendarPanel {

    private static final int HOLIDAY = 0xFFFF6B5E;
    private static final int HOLIDAY_ON_LIGHT = 0xFFC62828;
    private static final int FESTIVAL = 0xFFFFB23C;
    private static final int FESTIVAL_ON_LIGHT = 0xFF8A5300;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_ON_LIGHT = 0xFF111114;
    private static final int DIM = 0x99FFFFFF;
    private static final int DIM_ON_LIGHT = 0xB3111114;

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final Context ctx;
    private final float density;
    private final Typeface medium, body;
    private final LinearLayout panel, grid;
    private final TextView title, samvat, detailDate, detailPanchang, detailFestival;
    private final TextView prevYear, prevMonth, nextMonth, nextYear;
    private final LocalDate today = LocalDate.now(Panchang.ZONE);
    private YearMonth shown;
    private final List<View> cells = new ArrayList<>();

    CalendarPanel(Context ctx) {
        this.ctx = ctx;
        density = ctx.getResources().getDisplayMetrics().density;
        medium = UiFont.get(ctx, R.string.font_medium);
        body = UiFont.get(ctx, R.string.font_body);
        shown = YearMonth.from(today);

        panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setElevation(12 * density);
        int pad = dp(18);
        panel.setPadding(pad, dp(14), pad, dp(14));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        prevYear = navButton("«", ctx.getString(R.string.calendar_prev_year), () -> show(shown.minusYears(1), -1));
        prevMonth = navButton("‹", ctx.getString(R.string.calendar_prev_month), () -> show(shown.minusMonths(1), -1));
        nextMonth = navButton("›", ctx.getString(R.string.calendar_next_month), () -> show(shown.plusMonths(1), -1));
        nextYear = navButton("»", ctx.getString(R.string.calendar_next_year), () -> show(shown.plusYears(1), -1));
        title = text(22, medium, TEXT);
        title.setGravity(Gravity.CENTER);
        header.addView(prevYear);
        header.addView(prevMonth);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(nextMonth);
        header.addView(nextYear);
        panel.addView(header);

        samvat = text(13, body, FESTIVAL);
        samvat.setGravity(Gravity.CENTER);
        samvat.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(8);
        panel.addView(samvat, slp);

        LinearLayout weekdays = new LinearLayout(ctx);
        String[] names = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (int i = 0; i < 7; i++) {
            TextView w = text(11, medium, i == 0 ? HOLIDAY : DIM);
            w.setGravity(Gravity.CENTER);
            w.setLetterSpacing(0.12f);
            w.setText(names[i]);
            weekdays.addView(w, new LinearLayout.LayoutParams(0, dp(20), 1));
        }
        panel.addView(weekdays);

        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        panel.addView(grid);

        View line = new View(ctx);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        llp.setMargins(0, dp(8), 0, dp(8));
        panel.addView(line, llp);

        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout detail = new LinearLayout(ctx);
        detail.setOrientation(LinearLayout.VERTICAL);
        detailDate = text(15, medium, TEXT);
        detailPanchang = text(13, body, DIM);
        detailFestival = text(13, medium, FESTIVAL);
        detail.addView(detailDate);
        detail.addView(detailPanchang);
        detail.addView(detailFestival);
        footer.addView(detail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView legend = text(11, body, DIM);
        legend.setGravity(Gravity.END);
        legend.setText(TextUtils.concat(dot(HOLIDAY), " " + ctx.getString(R.string.calendar_holiday) + "\n",
                dot(FESTIVAL), " " + ctx.getString(R.string.calendar_festival)));
        footer.addView(legend);
        panel.addView(footer);

        show(shown, today.getDayOfMonth());
    }

    View view() {
        return panel;
    }

    /** Focus today, which {@link #show} picked when the popup opened. */
    void focusInitial() {
        int i = Math.min(today.getDayOfMonth(), cells.size()) - 1;
        if (i >= 0) cells.get(i).requestFocus();
    }

    /** Fill the grid for a month and focus a day of it, or keep focus where it is with {@code focusDay} -1. */
    private void show(YearMonth month, int focusDay) {
        if (month.getYear() < 1900 || month.getYear() > 2100) return;
        shown = month;
        List<Panchang.Day> days = Panchang.month(month.getYear(), month.getMonthValue());
        title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());

        Set<Integer> years = new LinkedHashSet<>();
        Set<String> months = new LinkedHashSet<>();
        for (Panchang.Day d : days) {
            years.add(d.samvat);
            months.add(d.monthName());
        }
        samvat.setText(ctx.getString(R.string.calendar_samvat, TextUtils.join(" / ", years))
                + "  ·  " + TextUtils.join(" – ", months));

        grid.removeAllViews();
        cells.clear();
        int lead = month.atDay(1).getDayOfWeek().getValue() % 7;   // Sunday first
        int rows = (lead + days.size() + 6) / 7;
        int cellHeight = dp(rows > 5 ? 52 : 58);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(ctx);
            for (int c = 0; c < 7; c++) {
                int index = r * 7 + c - lead;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cellHeight, 1);
                lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                if (index < 0 || index >= days.size()) {
                    row.addView(new View(ctx), lp);
                    continue;
                }
                View cell = cell(days.get(index), c == 0, index, days.size());
                cells.add(cell);
                row.addView(cell, lp);
            }
            grid.addView(row);
        }
        if (focusDay > 0) cells.get(Math.min(focusDay, cells.size()) - 1).requestFocus();
        else describe(days.get(Math.min(today.getDayOfMonth(), days.size()) - 1));
    }

    private View cell(Panchang.Day day, boolean sunday, int index, int count) {
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setBackground(cellBackground(day.date.equals(today)));

        boolean holiday = day.holiday || sunday;
        TextView number = text(19, medium, 0);
        number.setTextColor(states(holiday ? HOLIDAY : TEXT, holiday ? HOLIDAY_ON_LIGHT : TEXT_ON_LIGHT));
        number.setText(String.valueOf(day.date.getDayOfMonth()));
        TextView tithi = text(10, body, 0);
        boolean marked = day.tithi == 15 || day.tithi == 30;
        tithi.setTextColor(states(marked ? TEXT : DIM, marked ? TEXT_ON_LIGHT : DIM_ON_LIGHT));
        tithi.setText(day.shortLabel());
        cell.addView(number, wrap());
        cell.addView(tithi, wrap());
        if (!day.festival.isEmpty()) {
            TextView fest = text(9.5f, medium, 0);
            fest.setTextColor(states(day.holiday ? HOLIDAY : FESTIVAL, day.holiday ? HOLIDAY_ON_LIGHT : FESTIVAL_ON_LIGHT));
            fest.setText(day.festival);
            fest.setSingleLine(true);
            fest.setEllipsize(TextUtils.TruncateAt.END);
            fest.setPadding(dp(3), 0, dp(3), 0);
            cell.addView(fest, wrap());
        }
        for (int i = 0; i < cell.getChildCount(); i++) cell.getChildAt(i).setDuplicateParentStateEnabled(true);
        cell.setContentDescription(LONG_DATE.format(day.date) + ", " + day.longLabel()
                + (day.festival.isEmpty() ? "" : ", " + day.festival));

        cell.setOnFocusChangeListener((v, focused) -> {
            if (focused) describe(day);
        });
        cell.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (stepKey(keyCode)) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index == 0) {
                YearMonth prev = shown.minusMonths(1);
                show(prev, prev.lengthOfMonth());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == count - 1) {
                show(shown.plusMonths(1), 1);
                return true;
            }
            // Down from the last week stays put instead of hunting for something below the grid.
            int lead = leadOf(shown);
            return keyCode == KeyEvent.KEYCODE_DPAD_DOWN && (index + lead) / 7 == (count - 1 + lead) / 7;
        });
        cell.setOnClickListener(v -> describe(day));
        return cell;
    }

    private static int leadOf(YearMonth m) {
        return m.atDay(1).getDayOfWeek().getValue() % 7;
    }

    private void describe(Panchang.Day day) {
        detailDate.setText(LONG_DATE.format(day.date));
        detailPanchang.setText(day.longLabel() + "  ·  " + ctx.getString(R.string.calendar_samvat, String.valueOf(day.samvat)));
        detailFestival.setText(day.festival);
        detailFestival.setTextColor(day.holiday ? HOLIDAY : FESTIVAL);
        detailFestival.setVisibility(day.festival.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Month steps that work from any day: Channel/Page up and down, fast-forward and rewind. */
    private boolean stepKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                show(shown.minusMonths(1), 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                show(shown.plusMonths(1), 1);
                return true;
            default:
                return false;
        }
    }

    private TextView navButton(String glyph, String description, Runnable action) {
        TextView b = text(22, medium, 0);
        b.setTextColor(states(TEXT, TEXT_ON_LIGHT));
        b.setText(glyph);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(description);
        b.setBackground(cellBackground(false));
        b.setOnClickListener(v -> action.run());
        b.setOnKeyListener((v, keyCode, event) -> event.getAction() == KeyEvent.ACTION_DOWN
                && (stepKey(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_UP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(40));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    /** White when focused, like the Inputs rows; today keeps a thin ring. */
    private StateListDrawable cellBackground(boolean ring) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xF5FFFFFF);
        focused.setCornerRadius(dp(12));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(ring ? 0x1AFFFFFF : 0);
        normal.setCornerRadius(dp(12));
        if (ring) normal.setStroke(Math.max(1, Math.round(1.5f * density)), FESTIVAL);
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    private static ColorStateList states(int normal, int focused) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{focused, normal});
    }

    private CharSequence dot(int color) {
        android.text.SpannableString s = new android.text.SpannableString("●");
        s.setSpan(new android.text.style.ForegroundColorSpan(color), 0, 1, 0);
        return s;
    }

    private TextView text(float sp, Typeface face, int color) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        if (color != 0) t.setTextColor(color);
        return t;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
