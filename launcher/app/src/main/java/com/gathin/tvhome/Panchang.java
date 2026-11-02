package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Vikram Samvat (Purnimanta, North Indian) dates and Indian festivals for the calendar popup, computed offline.
 * The sun is Meeus' low-precision solar theory, the moon Meeus chapter 47 with its main longitude terms, which
 * puts tithi boundaries within a few minutes. Tithis are read at sunrise in New Delhi; a festival is read at the
 * time of day its observance follows (Diwali at dusk, Janmashtami at midnight), so a festival can still differ by a
 * day from a local panchang that uses another place or another rule. Pure maths, no Android types.
 */
final class Panchang {

    static final double LATITUDE = 28.6139;
    static final double LONGITUDE = 77.2090;
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int IST_MINUTES = 330;

    static final String[] MONTHS = {
            "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashvin", "Kartik", "Margashirsha", "Pausha", "Magha", "Phalguna"};
    private static final String[] TITHIS = {
            "Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami",
            "Ashtami", "Navami", "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi"};

    private Panchang() {
    }

    /** One Gregorian day with its sunrise tithi and the festivals that fall on it. */
    static final class Day {
        final LocalDate date;
        /** 1-15 Shukla paksha, 16-30 Krishna paksha (30 = Amavasya). */
        final int tithi;
        /** Purnimanta month, 0 = Chaitra. */
        final int month;
        final boolean adhika;
        final int samvat;
        /** Festival names joined with " · ", or "" for none. */
        final String festival;
        /** Whether one of the festivals is a central government holiday. */
        final boolean holiday;

        Day(LocalDate date, int tithi, int month, boolean adhika, int samvat, String festival, boolean holiday) {
            this.date = date;
            this.tithi = tithi;
            this.month = month;
            this.adhika = adhika;
            this.samvat = samvat;
            this.festival = festival;
            this.holiday = holiday;
        }

        boolean shukla() {
            return tithi <= 15;
        }

        /** "Pratipada" .. "Chaturdashi", "Purnima" or "Amavasya". */
        String tithiName() {
            if (tithi == 15) return "Purnima";
            if (tithi == 30) return "Amavasya";
            return TITHIS[(tithi - 1) % 15];
        }

        /** Short cell label: "Shukla 5", "Krishna 14", "Purnima", "Amavasya". */
        String shortLabel() {
            if (tithi == 15 || tithi == 30) return tithiName();
            return (shukla() ? "Shukla " : "Krishna ") + ((tithi - 1) % 15 + 1);
        }

        String monthName() {
            return (adhika ? "Adhik " : "") + MONTHS[month];
        }

        /** "Kartik Krishna Amavasya" or "Kartik Purnima". */
        String longLabel() {
            if (tithi == 15 || tithi == 30) return monthName() + " " + tithiName();
            return monthName() + (shukla() ? " Shukla " : " Krishna ") + tithiName();
        }
    }

    /** Every day of a Gregorian month. */
    static List<Day> month(int year, int month) {
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.plusMonths(1).minusDays(1);
        Lunations lunations = new Lunations(jd(first.minusDays(3), 0), jd(last.plusDays(3), 1440));
        List<Day> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) days.add(day(d, lunations));
        return days;
    }

    private static Day day(LocalDate d, Lunations lunations) {
        double sunrise = moment(d, When.SUNRISE);
        int tithi = tithiAt(sunrise);
        Lunation l = lunations.at(sunrise);
        int month = !l.adhika && tithi > 15 ? (l.month + 1) % 12 : l.month;
        StringBuilder names = new StringBuilder();
        boolean holiday = false;
        for (Fest f : FESTIVALS) {
            if (!f.on(d, lunations)) continue;
            if (names.length() > 0) names.append(" · ");
            names.append(f.name);
            holiday |= f.holiday;
        }
        return new Day(d, tithi, month, l.adhika, l.samvat(), names.toString(), holiday);
    }

    // ---- festivals ------------------------------------------------------------------------------

    private enum When { SUNRISE, MIDDAY, APARAHNA, SUNSET, MOONRISE, NISHITA }

    private abstract static class Fest {
        final String name;
        final boolean holiday;

        Fest(String name, boolean holiday) {
            this.name = name;
            this.holiday = holiday;
        }

        abstract boolean on(LocalDate d, Lunations lunations);
    }

    private static final class Fixed extends Fest {
        final int month, day;

        Fixed(String name, boolean holiday, int month, int day) {
            super(name, holiday);
            this.month = month;
            this.day = day;
        }

        @Override
        boolean on(LocalDate d, Lunations lunations) {
            return d.getMonthValue() == month && d.getDayOfMonth() == day;
        }
    }

    /** The day the sun enters a sidereal sign (0 = Mesha, 9 = Makara), shifted by {@code offset} days. */
    private static final class Sankranti extends Fest {
        final int sign, offset;

        Sankranti(String name, boolean holiday, int sign, int offset) {
            super(name, holiday);
            this.sign = sign;
            this.offset = offset;
        }

        @Override
        boolean on(LocalDate d, Lunations lunations) {
            LocalDate s = d.plusDays(-offset);
            return rashi(jd(s, 0)) != sign && rashi(jd(s, 1440)) == sign;
        }
    }

    /** Good Friday, two days before Gregorian Easter. */
    private static final class GoodFriday extends Fest {
        GoodFriday() {
            super("Good Friday", true);
        }

        @Override
        boolean on(LocalDate d, Lunations lunations) {
            return d.equals(easter(d.getYear()).minusDays(2));
        }
    }

    /**
     * A tithi of an amanta month (0 = Chaitra), read at a time of day: the first day whose moment falls in it.
     * When the tithi is skipped (kshaya), the day it begins takes the festival.
     */
    private static final class TithiFest extends Fest {
        final int month, tithi;
        final When when;

        TithiFest(String name, boolean holiday, int month, int tithi, When when) {
            super(name, holiday);
            this.month = month;
            this.tithi = tithi;
            this.when = when;
        }

        @Override
        boolean on(LocalDate d, Lunations lunations) {
            if (holds(d, lunations)) return !holds(d.minusDays(1), lunations);
            return d.equals(skipped(d.minusDays(1), lunations)) || d.equals(skipped(d, lunations));
        }

        private boolean holds(LocalDate d, Lunations lunations) {
            double at = moment(d, when);
            return tithiAt(at) == tithi && inMonth(lunations.at(at));
        }

        /** A tithi that covers neither this day's moment nor the next one's (kshaya) is kept on the date it begins. */
        private LocalDate skipped(LocalDate d, Lunations lunations) {
            int before = tithi == 1 ? 30 : tithi - 1, after = tithi == 30 ? 1 : tithi + 1;
            double at = moment(d, when);
            if (tithiAt(at) != before) return null;
            double next = moment(d.plusDays(1), when);
            if (tithiAt(next) != after || !inMonth(lunations.at(tithi == 30 ? at : next))) return null;
            double start = at, target = 12 * (tithi - 1);
            for (int i = 0; i < 6; i++) {
                double gap = target - elongation(start);
                if (gap > 180) gap -= 360;
                if (gap < -180) gap += 360;
                start += gap / 12.1908;
            }
            return start < jd(d, 1440) ? d : d.plusDays(1);
        }

        private boolean inMonth(Lunation l) {
            return !l.adhika && l.month == month;
        }
    }

    /**
     * Holika Dahan is lit at dusk on Phalguna Purnima once Bhadra (the first half of the tithi) is over; when Bhadra
     * runs past midnight the fire is lit after it, which falls on the next date. Holi is the day after the fire.
     */
    private static final class Holika extends Fest {
        final int offset;

        Holika(String name, boolean holiday, int offset) {
            super(name, holiday);
            this.offset = offset;
        }

        @Override
        boolean on(LocalDate d, Lunations lunations) {
            LocalDate x = d.minusDays(offset);
            for (LocalDate e = x.minusDays(1); !e.isAfter(x); e = e.plusDays(1)) {
                if (!purnimaAtDusk(e, lunations) || purnimaAtDusk(e.minusDays(1), lunations)) continue;
                LocalDate fire = bhadraEnd(e) > jd(e, 1440) ? e.plusDays(1) : e;
                if (fire.equals(x)) return true;
            }
            return false;
        }

        private static boolean purnimaAtDusk(LocalDate e, Lunations lunations) {
            double at = moment(e, When.SUNSET);
            Lunation l = lunations.at(at);
            return !l.adhika && l.month == 11 && tithiAt(at) == 15;
        }

        /** When the moon is 174 degrees from the sun, the middle of Purnima; dusk itself when that has passed. */
        private static double bhadraEnd(LocalDate e) {
            double jd = moment(e, When.SUNSET);
            if (elongation(jd) >= 174) return jd;
            for (int i = 0; i < 6; i++) jd += (174 - elongation(jd)) / 12.1908;
            return jd;
        }
    }

    private static final List<Fest> FESTIVALS = new ArrayList<>();

    static {
        FESTIVALS.add(new Fixed("Republic Day", true, 1, 26));
        FESTIVALS.add(new Fixed("Ambedkar Jayanti", false, 4, 14));
        FESTIVALS.add(new Fixed("Independence Day", true, 8, 15));
        FESTIVALS.add(new Fixed("Gandhi Jayanti", true, 10, 2));
        FESTIVALS.add(new Fixed("Christmas", true, 12, 25));
        FESTIVALS.add(new GoodFriday());
        FESTIVALS.add(new Sankranti("Lohri", false, 9, -1));
        FESTIVALS.add(new Sankranti("Makar Sankranti", false, 9, 0));
        FESTIVALS.add(new Sankranti("Baisakhi", false, 0, 0));
        FESTIVALS.add(new TithiFest("Vasant Panchami", false, 10, 5, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Maha Shivratri", false, 10, 29, When.NISHITA));
        FESTIVALS.add(new Holika("Holika Dahan", false, 0));
        FESTIVALS.add(new Holika("Holi", true, 1));
        FESTIVALS.add(new TithiFest("Ugadi \u00B7 Gudi Padwa", false, 0, 1, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Ram Navami", true, 0, 9, When.MIDDAY));
        FESTIVALS.add(new TithiFest("Mahavir Jayanti", true, 0, 13, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Hanuman Jayanti", false, 0, 15, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Akshaya Tritiya", false, 1, 3, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Buddha Purnima", true, 1, 15, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Raksha Bandhan", false, 4, 15, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Janmashtami", true, 4, 23, When.NISHITA));
        FESTIVALS.add(new TithiFest("Ganesh Chaturthi", false, 5, 4, When.MIDDAY));
        FESTIVALS.add(new TithiFest("Navratri begins", false, 6, 1, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Durga Ashtami", false, 6, 8, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Dussehra", true, 6, 10, When.APARAHNA));
        FESTIVALS.add(new TithiFest("Karwa Chauth", false, 6, 19, When.MOONRISE));
        FESTIVALS.add(new TithiFest("Dhanteras", false, 6, 28, When.SUNSET));
        FESTIVALS.add(new TithiFest("Diwali", true, 6, 30, When.SUNSET));
        FESTIVALS.add(new TithiFest("Govardhan Puja", false, 7, 1, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Bhai Dooj", false, 7, 2, When.SUNRISE));
        FESTIVALS.add(new TithiFest("Chhath Puja", false, 7, 6, When.SUNSET));
        FESTIVALS.add(new TithiFest("Guru Nanak Jayanti", true, 7, 15, When.SUNRISE));
    }

    /** Gregorian Easter Sunday (anonymous algorithm). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(y, month, day);
    }

    // ---- lunar months ---------------------------------------------------------------------------

    /** An amanta month: from one new moon to the next, named for the sign the sun is in when it starts. */
    private static final class Lunation {
        final double start;
        final int month;
        final boolean adhika;

        Lunation(double start, int month, boolean adhika) {
            this.start = start;
            this.month = month;
            this.adhika = adhika;
        }

        /** The year turns at Chaitra Shukla Pratipada, so Pausha (from January), Magha and Phalguna are the old year. */
        int samvat() {
            LocalDate s = LocalDate.ofEpochDay((long) Math.floor(start - 2440587.5 + IST_MINUTES / 1440.0));
            boolean oldYear = month >= 9 && s.getMonthValue() <= 4;
            return s.getYear() + (oldYear ? 56 : 57);
        }
    }

    private static final class Lunations {
        final List<Lunation> list = new ArrayList<>();

        Lunations(double from, double to) {
            double nm = newMoonBefore(from - 32);
            List<Double> moons = new ArrayList<>();
            while (nm < to + 32) {
                moons.add(nm);
                nm = newMoonNear(nm + 29.53);
            }
            for (int i = 0; i + 1 < moons.size(); i++) {
                int sign = rashi(moons.get(i));
                boolean adhika = sign == rashi(moons.get(i + 1));
                list.add(new Lunation(moons.get(i), (sign + 1) % 12, adhika));
            }
        }

        Lunation at(double jd) {
            Lunation found = list.get(0);
            for (Lunation l : list) {
                if (l.start <= jd) found = l;
                else break;
            }
            return found;
        }
    }

    // ---- astronomy ------------------------------------------------------------------------------

    /** Julian day (UT) of a local IST time on a date, in minutes after midnight. */
    static double jd(LocalDate d, double minutes) {
        return d.toEpochDay() + 2440587.5 + (minutes - IST_MINUTES) / 1440.0;
    }

    private static double moment(LocalDate d, When when) {
        int[] rs = Sun.riseAndSet(d, LATITUDE, LONGITUDE, ZONE);
        int rise = rs == null ? 360 : rs[0], set = rs == null ? 1080 : rs[1];
        switch (when) {
            case MIDDAY: return jd(d, (rise + set) / 2.0);
            case APARAHNA: return jd(d, rise + 0.7 * (set - rise));
            case SUNSET: return jd(d, set);
            case MOONRISE: return jd(d, set + 150);
            case NISHITA: return jd(d, set + (1440 - set + rise) / 2.0);
            default: return jd(d, rise);
        }
    }

    /** 1..30: which twelve-degree step of the moon's elongation from the sun. */
    static int tithiAt(double jd) {
        return (int) Math.floor(elongation(jd) / 12) + 1;
    }

    /** Sidereal sign (Lahiri) of the sun, 0 = Mesha. */
    static int rashi(double jd) {
        double t = centuries(jd);
        double ayanamsa = 23.85709 + 1.396971 * t;
        return (int) Math.floor(norm(sunLongitude(t) - ayanamsa) / 30);
    }

    private static double newMoonBefore(double jd) {
        double k = Math.floor((jd - 2451550.09766) / 29.530588861);
        double nm = newMoonNear(2451550.09766 + 29.530588861 * k);
        while (nm > jd) nm = newMoonNear(nm - 29.53);
        return nm;
    }

    private static double newMoonNear(double jd) {
        for (int i = 0; i < 8; i++) {
            double e = elongation(jd);
            if (e > 180) e -= 360;
            jd -= e / 12.1908;
        }
        return jd;
    }

    private static double elongation(double jd) {
        double t = centuries(jd);
        return norm(moonLongitude(t) - sunLongitude(t));
    }

    /** Julian centuries of TT since J2000; ΔT is about 69 s this century. */
    private static double centuries(double jdUt) {
        return (jdUt + 69.0 / 86400 - 2451545.0) / 36525;
    }

    /** Apparent geocentric longitude of the sun, degrees (Meeus chapter 25, low precision). */
    static double sunLongitude(double t) {
        double l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        double m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        double c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * Math.sin(m)
                + (0.019993 - 0.000101 * t) * Math.sin(2 * m) + 0.000289 * Math.sin(3 * m);
        double omega = Math.toRadians(125.04 - 1934.136 * t);
        return norm(l0 + c - 0.00569 - 0.00478 * Math.sin(omega));
    }

    // Meeus table 47.A, longitude terms: D, M, M', F, coefficient in millionths of a degree.
    private static final int[][] MOON = {
            {0, 0, 1, 0, 6288774}, {2, 0, -1, 0, 1274027}, {2, 0, 0, 0, 658314}, {0, 0, 2, 0, 213618},
            {0, 1, 0, 0, -185116}, {0, 0, 0, 2, -114332}, {2, 0, -2, 0, 58793}, {2, -1, -1, 0, 57066},
            {2, 0, 1, 0, 53322}, {2, -1, 0, 0, 45758}, {0, 1, -1, 0, -40923}, {1, 0, 0, 0, -34720},
            {0, 1, 1, 0, -30383}, {2, 0, 0, -2, 15327}, {0, 0, 1, 2, -12528}, {0, 0, 1, -2, 10980},
            {4, 0, -1, 0, 10675}, {0, 0, 3, 0, 10034}, {4, 0, -2, 0, 8548}, {2, 1, -1, 0, -7888},
            {2, 1, 0, 0, -6766}, {1, 0, -1, 0, -5163}, {1, 1, 0, 0, 4987}, {2, -1, 1, 0, 4036},
            {2, 0, 2, 0, 3994}, {4, 0, 0, 0, 3861}, {2, 0, -3, 0, 3665}, {0, 1, -2, 0, -2689},
            {2, 0, -1, 2, -2602}, {2, -1, -2, 0, 2390}, {1, 0, 1, 0, -2348}, {2, -2, 0, 0, 2236},
            {0, 1, 2, 0, -2120}, {0, 2, 0, 0, -2069}, {2, -2, -1, 0, 2048}, {2, 0, 1, -2, -1773},
            {2, 0, 0, 2, -1595}, {4, -1, -1, 0, 1215}, {0, 0, 2, 2, -1110}, {3, 0, -1, 0, -892},
            {2, 1, 1, 0, -810}, {4, -1, -2, 0, 759}, {0, 2, -1, 0, -713}, {2, 2, -1, 0, -700},
            {2, 1, -2, 0, 691}, {2, -1, 0, -2, 596}, {4, 0, 1, 0, 549}, {0, 0, 4, 0, 537},
            {4, -1, 0, 0, 520}, {1, 0, -2, 0, -487}, {2, 1, 0, -2, -399}, {0, 0, 2, -2, -381},
            {1, 1, 1, 0, 351}, {3, 0, -2, 0, -340}, {4, 0, -3, 0, 330}, {2, -1, 2, 0, 327},
            {0, 2, 1, 0, -323}, {1, 1, -1, 0, 299}, {2, 0, 3, 0, 294}};

    /** Geocentric longitude of the moon without nutation, degrees (Meeus chapter 47). */
    static double moonLongitude(double t) {
        double t2 = t * t, t3 = t2 * t, t4 = t3 * t;
        double lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841 - t4 / 65194000;
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868 - t4 / 113065000);
        double m = Math.toRadians(357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000);
        double mp = Math.toRadians(134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699 - t4 / 14712000);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000 + t4 / 863310000);
        double e = 1 - 0.002516 * t - 0.0000074 * t2;
        double sum = 0;
        for (int[] row : MOON) {
            double term = row[4] * Math.sin(row[0] * d + row[1] * m + row[2] * mp + row[3] * f);
            if (row[1] == 1 || row[1] == -1) term *= e;
            else if (row[1] == 2 || row[1] == -2) term *= e * e;
            sum += term;
        }
        double a1 = Math.toRadians(119.75 + 131.849 * t), a2 = Math.toRadians(53.09 + 479264.290 * t);
        sum += 3958 * Math.sin(a1) + 1962 * Math.sin(Math.toRadians(lp) - f) + 318 * Math.sin(a2);
        return norm(lp + sum / 1e6);
    }

    private static double norm(double deg) {
        double r = deg % 360;
        return r < 0 ? r + 360 : r;
    }
}
