package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.PopupWindow;
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
    // Outfit is thin at 10sp, so the secondary lines need more contrast than a translucent grey gives them.
    private static final int DIM = 0xBFFFFFFF;
    private static final int DIM_ON_LIGHT = 0xFF3A3A40;

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final Context ctx;
    private final float density;
    private final Typeface medium, body;
    private final LinearLayout panel, grid;
    private final TextView title, samvat, detailDate, detailPanchang, detailTiming, detailFestival, source;
    private final TextView prevYear, prevMonth, nextMonth, nextYear;
    private final LocalDate today = LocalDate.now(Panchang.ZONE);
    private final CalendarData data;
    private YearMonth shown;
    private List<Panchang.Day> days = new ArrayList<>();
    private final List<View> cells = new ArrayList<>();
    /** The day cell that has (or last had) focus, kept across a redraw when downloads arrive. */
    private int focusedIndex = -1;

    CalendarPanel(Context ctx) {
        this.ctx = ctx;
        data = CalendarData.get(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        medium = UiFont.get(ctx, R.string.font_medium);
        // font_body (variable Outfit) comes out hairline-thin at 10-13sp on this panel; Regular keeps small lines legible.
        body = UiFont.get(ctx, R.string.font_regular);
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
        String[] names = {"रवि", "सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि"};
        for (int i = 0; i < 7; i++) {
            TextView w = text(12, medium, i == 0 ? HOLIDAY : DIM);
            w.setGravity(Gravity.CENTER);
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
        detailTiming = text(12, body, DIM);
        detailFestival = text(13, medium, FESTIVAL);
        detail.addView(detailDate);
        detail.addView(detailPanchang);
        detail.addView(detailTiming);
        detail.addView(detailFestival);
        footer.addView(detail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView legend = text(11, body, DIM);
        legend.setGravity(Gravity.END);
        legend.setText(TextUtils.concat(dot(HOLIDAY), " सार्वजनिक अवकाश   ", dot(FESTIVAL), " त्योहार\nOK: चौघड़िया"));
        source = text(10, body, DIM);
        source.setGravity(Gravity.END);
        source.setPadding(0, dp(4), 0, 0);
        right.addView(legend);
        right.addView(source);
        footer.addView(right);
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
        if (!month.equals(shown)) focusedIndex = -1;
        shown = month;
        days = data.month(month);
        render(focusDay);
        // Draw now from the cache and the computation; fill in when downloads arrive, keeping the focused day.
        data.refresh(month, updated -> {
            if (!updated.equals(shown) || !panel.isAttachedToWindow()) return;
            boolean inGrid = focusedIndex >= 0 && focusedIndex < cells.size() && cells.get(focusedIndex).hasFocus();
            int keep = focusedIndex;
            days = data.month(updated);
            render(inGrid ? keep + 1 : -1);
        });
    }

    private void render(int focusDay) {
        YearMonth month = shown;
        title.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());

        Set<String> years = new LinkedHashSet<>();
        Set<String> months = new LinkedHashSet<>();
        for (Panchang.Day d : days) {
            years.add(Panchang.deva(d.samvat));
            months.add(d.monthHi());
        }
        samvat.setText("विक्रम संवत " + TextUtils.join(" / ", years) + "  ·  " + TextUtils.join(" – ", months));

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
        if (focusDay > 0) {
            cells.get(Math.min(focusDay, cells.size()) - 1).requestFocus();
        } else {
            int i = focusedIndex >= 0 && focusedIndex < days.size() ? focusedIndex : Math.min(today.getDayOfMonth(), days.size()) - 1;
            describe(days.get(i));
        }
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
        tithi.setText(day.shortHi());
        tithi.setSingleLine(true);
        // Before addView: a ViewGroup only passes its state changes on to children that duplicate it when they are
        // added; set afterwards, the text keeps the colour of its first draw (white on the white focus, dark on today).
        number.setDuplicateParentStateEnabled(true);
        tithi.setDuplicateParentStateEnabled(true);
        cell.addView(number, wrap());
        cell.addView(tithi, wrap());
        if (!day.festival.isEmpty()) {
            TextView fest = text(9.5f, medium, 0);
            fest.setTextColor(states(day.holiday ? HOLIDAY : FESTIVAL, day.holiday ? HOLIDAY_ON_LIGHT : FESTIVAL_ON_LIGHT));
            fest.setText(day.festival);
            fest.setSingleLine(true);
            fest.setEllipsize(TextUtils.TruncateAt.END);
            fest.setPadding(dp(3), 0, dp(3), 0);
            fest.setDuplicateParentStateEnabled(true);
            cell.addView(fest, wrap());
        }
        cell.setContentDescription(LONG_DATE.format(day.date) + ", " + day.longHi()
                + (day.festival.isEmpty() ? "" : ", " + day.festival));

        cell.setOnFocusChangeListener((v, focused) -> {
            if (!focused) return;
            focusedIndex = index;
            describe(day);
        });
        // OK opens choghadiya only for a press that started on this cell: the release of the OK that opened the
        // calendar arrives here too, and must not open it straight away.
        final boolean[] pressed = {false};
        cell.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) pressed[0] = true;
                } else if (event.getAction() == KeyEvent.ACTION_UP && pressed[0]) {
                    pressed[0] = false;
                    showChoghadiya(day.date);
                }
                return true;
            }
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
        cell.setOnClickListener(v -> showChoghadiya(day.date));
        return cell;
    }

    private static int leadOf(YearMonth m) {
        return m.atDay(1).getDayOfWeek().getValue() % 7;
    }

    private void describe(Panchang.Day day) {
        detailDate.setText(LONG_DATE.format(day.date));
        detailPanchang.setText(day.longHi() + "  ·  विक्रम संवत " + Panchang.deva(day.samvat));
        detailTiming.setText(timing(day));
        detailTiming.setVisibility(day.tithiEnds == null ? View.GONE : View.VISIBLE);
        detailFestival.setText(day.festival);
        detailFestival.setTextColor(day.holiday ? HOLIDAY : FESTIVAL);
        detailFestival.setVisibility(day.festival.isEmpty() ? View.GONE : View.VISIBLE);
        source.setText("तिथि: " + (day.tithiOnline ? "Jagannatha Hora" : "टीवी पर गणना")
                + "\nत्योहार: " + (day.festivalOnline ? "Google कैलेंडर" : "टीवी पर गणना"));
    }

    /**
     * When the day's tithis change: "चतुर्दशी 11:29 AM तक, फिर अमावस्या"; a tithi over two sunrises
     * "नवमी पूरे दिन, कल 7:07 AM तक"; a skipped (kshaya) tithi "पंचमी 6:13 AM तक, फिर षष्ठी कल 4:27 AM तक (क्षय), फिर सप्तमी".
     */
    private String timing(Panchang.Day day) {
        if (day.tithiEnds == null) return "";
        String now = Panchang.Day.tithiHi(day.tithi);
        if (day.repeats) return now + " पूरे दिन, " + when(day.tithiEnds, day.date) + " तक";
        int next = day.tithi % 30 + 1;
        StringBuilder b = new StringBuilder(now).append(' ').append(when(day.tithiEnds, day.date)).append(" तक, फिर ");
        if (day.skipped > 0 && day.skippedEnds != null) {
            b.append(Panchang.Day.tithiHi(day.skipped)).append(' ').append(when(day.skippedEnds, day.date))
                    .append(" तक (क्षय), फिर ");
            next = day.skipped % 30 + 1;
        }
        return b.append(Panchang.Day.tithiHi(next)).toString();
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** "11:29 AM", or "कल 7:07 AM" when it falls on the next date. */
    private static String when(java.time.LocalDateTime t, LocalDate day) {
        return (t.toLocalDate().isAfter(day) ? "कल " : "") + CLOCK.format(t);
    }

    // ---- choghadiya -------------------------------------------------------------------------------

    private static final int GOOD = 0xFF7CDB95;

    private PopupWindow choghadiyaPopup;

    /** The calendar closed (Back, Home, another app): take the choghadiya window with it. */
    void close() {
        if (choghadiyaPopup != null) choghadiyaPopup.dismiss();
        choghadiyaPopup = null;
    }

    /** OK on a day: its 16 choghadiyas over the calendar; Left/Right step a day, Back returns to the month. */
    private void showChoghadiya(LocalDate date) {
        close();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.glass_panel);
        box.setElevation(16 * density);
        box.setPadding(dp(22), dp(16), dp(22), dp(16));
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        PopupWindow popup = new PopupWindow(box, dp(620), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // Back dismisses
        choghadiyaPopup = popup;
        fillChoghadiya(box, date);
        final LocalDate[] at = {date};
        box.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                at[0] = at[0].plusDays(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                fillChoghadiya(box, at[0]);
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
        });
        // Anchored to the activity's window: a popup may not be added on top of another popup's window token.
        View anchor = ctx instanceof android.app.Activity ? ((android.app.Activity) ctx).getWindow().getDecorView() : panel.getRootView();
        popup.showAtLocation(anchor, Gravity.CENTER, 0, 0);
        box.requestFocus();
    }

    private void fillChoghadiya(LinearLayout box, LocalDate date) {
        box.removeAllViews();
        TextView heading = text(20, medium, TEXT);
        heading.setText("चौघड़िया");
        TextView sub = text(13, body, DIM);
        sub.setText(LONG_DATE.format(date) + "   ‹ ›  दिन बदलें");
        box.addView(heading);
        box.addView(sub);

        List<Panchang.Period> periods = data.choghadiya(date);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(Panchang.ZONE);
        LinearLayout columns = new LinearLayout(ctx);
        columns.setPadding(0, dp(10), 0, dp(6));
        for (int part = 0; part < 2; part++) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView label = text(13, medium, FESTIVAL);
            label.setText(part == 0 ? "दिन · सूर्योदय से सूर्यास्त" : "रात · सूर्यास्त से सूर्योदय");
            label.setPadding(dp(8), 0, 0, dp(4));
            col.addView(label);
            for (Panchang.Period p : periods) {
                if (p.night != (part == 1)) continue;
                LinearLayout row = new LinearLayout(ctx);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(4), dp(8), dp(4));
                boolean current = !now.isBefore(p.start) && now.isBefore(p.end);
                if (current) {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0x33FFFFFF);
                    bg.setCornerRadius(dp(8));
                    row.setBackground(bg);
                }
                boolean good = Panchang.goodChoghadiya(p.kind);
                TextView name = text(15, medium, good ? GOOD : HOLIDAY);
                name.setText((good ? "● " : "○ ") + Panchang.CHOGHADIYA_HI[p.kind] + (current ? "  ← अभी" : ""));
                TextView time = text(13, body, TEXT);
                time.setText(CLOCK.format(p.start) + " – " + CLOCK.format(p.end));
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                row.addView(time);
                col.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (part == 0) lp.rightMargin = dp(16);
            columns.addView(col, lp);
        }
        box.addView(columns);
        TextView foot = text(11, body, DIM);
        foot.setText(TextUtils.concat(dot(GOOD), " शुभ समय   ", dot(HOLIDAY), " टालें   ·  स्रोत: ",
                data.choghadiyaOnline(date) ? "Jagannatha Hora" : "टीवी पर गणना (नई दिल्ली)"));
        box.addView(foot);
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

    /** White when focused, like the Inputs rows; today, unfocused, a gold-tinted fill with a gold ring. */
    private StateListDrawable cellBackground(boolean today) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xF5FFFFFF);
        focused.setCornerRadius(dp(12));
        if (today) focused.setStroke(Math.max(2, Math.round(2f * density)), FESTIVAL);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(today ? 0x40FFB23C : 0);
        normal.setCornerRadius(dp(12));
        if (today) normal.setStroke(Math.max(2, Math.round(2f * density)), FESTIVAL);
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
