package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * One Gregorian day with its sunrise tithi and the festivals that fall on it. The fields after {@code date} start
     * out computed here and may be replaced by online data (CalendarData), which is why they are not final.
     */
    static final class Day {
        final LocalDate date;
        /** 1-15 Shukla paksha, 16-30 Krishna paksha (30 = Amavasya): the tithi at sunrise (udaya tithi). */
        int tithi;
        /** When the sunrise tithi ends (IST); after the next sunrise when the tithi spans two days. */
        LocalDateTime tithiEnds;
        /** A whole tithi that begins and ends before the next sunrise (kshaya, "combined" with this day), or 0. */
        int skipped;
        /** When the skipped tithi ends, or null. */
        LocalDateTime skippedEnds;
        /** Whether the sunrise tithi is still running at the next sunrise, so tomorrow has the same tithi. */
        boolean repeats;
        /** Purnimanta month, 0 = Chaitra. */
        int month;
        boolean adhika;
        int samvat;
        /** Festival names joined with " · ", or "" for none. */
        String festival;
        /** Whether one of the festivals is a public holiday. */
        boolean holiday;
        /** Where the tithi, month and samvat came from, and where the festivals came from: true = online. */
        boolean tithiOnline, festivalOnline;

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
            return tithiName(tithi);
        }

        static String tithiName(int t) {
            if (t == 15) return "Purnima";
            if (t == 30) return "Amavasya";
            return TITHIS[(t - 1) % 15];
        }

        /** Short cell label: "Shukla 5", "Krishna 14", "Purnima", "Amavasya"; with a combined tithi "Krishna 5/6". */
        String shortLabel() {
            String first = shortLabel(tithi);
            if (skipped == 0) return first;
            boolean samePaksha = (tithi <= 15) == (skipped <= 15) && tithi != 15 && tithi != 30 && skipped != 15 && skipped != 30;
            return first + "/" + (samePaksha ? String.valueOf((skipped - 1) % 15 + 1) : shortLabel(skipped));
        }

        static String shortLabel(int t) {
            if (t == 15 || t == 30) return tithiName(t);
            return (t <= 15 ? "Shukla " : "Krishna ") + ((t - 1) % 15 + 1);
        }

        String monthName() {
            return (adhika ? "Adhik " : "") + MONTHS[month];
        }

        /** "Kartik Krishna Amavasya" or "Kartik Purnima". */
        String longLabel() {
            if (tithi == 15 || tithi == 30) return monthName() + " " + tithiName();
            return monthName() + (shukla() ? " Shukla " : " Krishna ") + tithiName();
        }

        /** Festival names in English (the tests compare these); {@link #festival} is the shown, Hindi text. */
        String festivalEn = "";
        /** The festivals computed here, so online ones can be matched against them. */
        List<Fest> fests = new ArrayList<>();

        // ---- Devanagari, as the calendar shows it ----

        /** "शुक्ल ५", "कृष्ण १४", "पूर्णिमा", "अमावस्या"; with a combined tithi "कृष्ण ५/६". */
        String shortHi() {
            String first = shortHi(tithi);
            if (skipped == 0) return first;
            boolean samePaksha = (tithi <= 15) == (skipped <= 15) && tithi != 15 && tithi != 30 && skipped != 15 && skipped != 30;
            return first + "/" + (samePaksha ? deva((skipped - 1) % 15 + 1) : shortHi(skipped));
        }

        static String shortHi(int t) {
            if (t == 15 || t == 30) return tithiHi(t);
            return (t <= 15 ? "शुक्ल " : "कृष्ण ") + deva((t - 1) % 15 + 1);
        }

        /** "प्रतिपदा" .. "चतुर्दशी", "पूर्णिमा" or "अमावस्या". */
        static String tithiHi(int t) {
            if (t == 15) return "पूर्णिमा";
            if (t == 30) return "अमावस्या";
            return TITHIS_HI[(t - 1) % 15];
        }

        String monthHi() {
            return (adhika ? "अधिक " : "") + MONTHS_HI[month];
        }

        /** "कार्तिक कृष्ण चतुर्दशी" or "कार्तिक पूर्णिमा". */
        String longHi() {
            if (tithi == 15 || tithi == 30) return monthHi() + " " + tithiHi(tithi);
            return monthHi() + (shukla() ? " शुक्ल " : " कृष्ण ") + tithiHi(tithi);
        }
    }

    static final String[] MONTHS_HI = {
            "चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद",
            "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन"};
    private static final String[] TITHIS_HI = {
            "प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पंचमी", "षष्ठी", "सप्तमी",
            "अष्टमी", "नवमी", "दशमी", "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी"};

    /** A number in Devanagari digits: 2083 -> "२०८३". */
    static String deva(int n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) b.append(c >= '0' && c <= '9' ? (char) ('०' + (c - '0')) : c);
        return b.toString();
    }

    // ---- choghadiya ---------------------------------------------------------------------------

    /** The seven choghadiyas in their cyclic order; Shubh, Labh, Amrit and Char are the good ones. */
    static final String[] CHOGHADIYA_HI = {"उद्वेग", "चर", "लाभ", "अमृत", "काल", "शुभ", "रोग"};

    static boolean goodChoghadiya(int kind) {
        return kind == 1 || kind == 2 || kind == 3 || kind == 5;
    }

    /** One of the 16 choghadiyas of a day: eight from sunrise to sunset, eight from sunset to the next sunrise. */
    static final class Period {
        final int kind;
        final boolean night;
        final LocalDateTime start, end;

        Period(int kind, boolean night, LocalDateTime start, LocalDateTime end) {
            this.kind = kind;
            this.night = night;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * The day's choghadiyas in New Delhi, computed: day and night each split into eight equal parts. The day starts
     * with Udveg on Sunday and steps three places a weekday (Amrit on Monday, Rog on Tuesday ...), each part one place
     * on; the night starts five places after the day's first and steps back two.
     */
    static List<Period> choghadiya(LocalDate d) {
        LocalDateTime rise = sunrise(d), set = ist(moment(d, When.SUNSET)), nextRise = sunrise(d.plusDays(1));
        int first = (3 * (d.getDayOfWeek().getValue() % 7)) % 7;
        List<Period> periods = new ArrayList<>();
        long day = java.time.Duration.between(rise, set).getSeconds(), night = java.time.Duration.between(set, nextRise).getSeconds();
        for (int i = 0; i < 8; i++) {
            periods.add(new Period((first + i) % 7, false, rise.plusSeconds(day * i / 8), rise.plusSeconds(day * (i + 1) / 8)));
        }
        int nightFirst = (first + 5) % 7;
        for (int i = 0; i < 8; i++) {
            periods.add(new Period(Math.floorMod(nightFirst - 2 * i, 7), true,
                    set.plusSeconds(night * i / 8), set.plusSeconds(night * (i + 1) / 8)));
        }
        return periods;
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
        StringBuilder names = new StringBuilder(), english = new StringBuilder();
        boolean holiday = false;
        List<Fest> fests = new ArrayList<>();
        for (Fest f : FESTIVALS) {
            if (!f.on(d, lunations)) continue;
            if (names.length() > 0) {
                names.append(" · ");
                english.append(" · ");
            }
            names.append(f.hindi);
            english.append(f.name);
            holiday |= f.holiday;
            fests.add(f);
        }
        Day day = new Day(d, tithi, month, l.adhika, l.samvat(), names.toString(), holiday);
        day.festivalEn = english.toString();
        day.fests = fests;
        double nextSunrise = moment(d.plusDays(1), When.SUNRISE);
        double ends = tithiEnd(tithi, sunrise);
        day.tithiEnds = ist(ends);
        day.repeats = ends > nextSunrise;
        if (ends < nextSunrise) {
            int next = tithi % 30 + 1;
            double nextEnds = tithiEnd(next, ends + 0.01);
            if (nextEnds < nextSunrise) {
                day.skipped = next;
                day.skippedEnds = ist(nextEnds);
            }
        }
        return day;
    }

    /** When tithi {@code t} (running at {@code from}) ends: the moon's elongation reaches 12 * t degrees. */
    static double tithiEnd(int t, double from) {
        double target = 12.0 * t, jd = from;
        for (int i = 0; i < 8; i++) {
            double gap = target - elongation(jd);
            if (gap > 180) gap -= 360;
            if (gap < -180) gap += 360;
            jd += gap / 12.1908;
        }
        return jd;
    }

    /** The end of tithi {@code t} that runs at sunrise on {@code d}, in IST; for an online tithi with no end time. */
    static LocalDateTime tithiEnd(int t, LocalDate d) {
        return ist(tithiEnd(t, moment(d, When.SUNRISE)));
    }

    /** The end of tithi {@code t}, which begins at or just after {@code after} (IST). */
    static LocalDateTime tithiEndAfter(int t, LocalDateTime after) {
        return ist(tithiEnd(t, jd(after.toLocalDate(), after.getHour() * 60 + after.getMinute()) + 0.01));
    }

    /** Sunrise in New Delhi on a date, IST. */
    static LocalDateTime sunrise(LocalDate d) {
        return ist(moment(d, When.SUNRISE));
    }

    /** Julian day (UT) to IST, to the minute. */
    static LocalDateTime ist(double jd) {
        double days = jd - 2440587.5 + IST_MINUTES / 1440.0;
        long day = (long) Math.floor(days);
        long minute = Math.round((days - day) * 1440);
        return LocalDate.ofEpochDay(day).atStartOfDay().plusMinutes(minute);
    }

    // ---- festivals ------------------------------------------------------------------------------

    private enum When { SUNRISE, MIDDAY, APARAHNA, SUNSET, MOONRISE, NISHITA }

    abstract static class Fest {
        final String name;
        final boolean holiday;
        /** The Hindi name shown on the calendar. */
        String hindi;
        /** Words that identify this festival in Google's Hindi holiday names, so an online entry replaces it. */
        String[] match;

        Fest(String name, boolean holiday) {
            this.name = name;
            this.holiday = holiday;
        }

        Fest hi(String hindi, String... match) {
            this.hindi = hindi;
            this.match = match.length > 0 ? match : new String[]{hindi};
            return this;
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
        FESTIVALS.add(new Fixed("Republic Day", true, 1, 26).hi("गणतंत्र दिवस"));
        FESTIVALS.add(new Fixed("Ambedkar Jayanti", false, 4, 14).hi("अम्बेडकर जयंती"));
        FESTIVALS.add(new Fixed("Independence Day", true, 8, 15).hi("स्वतंत्रता दिवस"));
        FESTIVALS.add(new Fixed("Gandhi Jayanti", true, 10, 2).hi("गांधी जयंती"));
        FESTIVALS.add(new Fixed("Christmas", true, 12, 25).hi("क्रिसमस"));
        FESTIVALS.add(new GoodFriday().hi("गुड फ्राइडे"));
        FESTIVALS.add(new Sankranti("Lohri", false, 9, -1).hi("लोहड़ी"));
        FESTIVALS.add(new Sankranti("Makar Sankranti", false, 9, 0).hi("मकर संक्रांति"));
        FESTIVALS.add(new Sankranti("Baisakhi", false, 0, 0).hi("बैसाखी", "वैसाखी"));
        FESTIVALS.add(new TithiFest("Vasant Panchami", false, 10, 5, When.SUNRISE).hi("वसंत पंचमी"));
        FESTIVALS.add(new TithiFest("Maha Shivratri", false, 10, 29, When.NISHITA).hi("महा शिवरात्रि", "शिवरात्रि"));
        FESTIVALS.add(new Holika("Holika Dahan", false, 0).hi("होलिका दहन"));
        FESTIVALS.add(new Holika("Holi", true, 1).hi("होली"));
        FESTIVALS.add(new TithiFest("Ugadi \u00B7 Gudi Padwa", false, 0, 1, When.SUNRISE).hi("गुड़ी पड़वा \u00B7 उगादि", "पड़वा", "उगादि"));
        FESTIVALS.add(new TithiFest("Ram Navami", true, 0, 9, When.MIDDAY).hi("राम नवमी", "रामनवमी", "राम नवमी"));
        FESTIVALS.add(new TithiFest("Mahavir Jayanti", true, 0, 13, When.SUNRISE).hi("महावीर जयंती"));
        FESTIVALS.add(new TithiFest("Hanuman Jayanti", false, 0, 15, When.SUNRISE).hi("हनुमान जयंती"));
        FESTIVALS.add(new TithiFest("Akshaya Tritiya", false, 1, 3, When.SUNRISE).hi("अक्षय तृतीया"));
        FESTIVALS.add(new TithiFest("Buddha Purnima", true, 1, 15, When.SUNRISE).hi("बुद्ध पूर्णिमा"));
        FESTIVALS.add(new TithiFest("Raksha Bandhan", false, 4, 15, When.SUNRISE).hi("रक्षा बंधन"));
        FESTIVALS.add(new TithiFest("Janmashtami", true, 4, 23, When.NISHITA).hi("जन्माष्टमी"));
        FESTIVALS.add(new TithiFest("Ganesh Chaturthi", false, 5, 4, When.MIDDAY).hi("गणेश चतुर्थी"));
        FESTIVALS.add(new TithiFest("Navratri begins", false, 6, 1, When.SUNRISE).hi("नवरात्रि आरंभ", "नवरात्रि"));
        FESTIVALS.add(new TithiFest("Durga Ashtami", false, 6, 8, When.SUNRISE).hi("दुर्गा अष्टमी", "अष्टमी"));
        FESTIVALS.add(new TithiFest("Dussehra", true, 6, 10, When.APARAHNA).hi("दशहरा"));
        FESTIVALS.add(new TithiFest("Karwa Chauth", false, 6, 19, When.MOONRISE).hi("करवा चौथ", "चतुर्थी", "करवा"));
        FESTIVALS.add(new TithiFest("Dhanteras", false, 6, 28, When.SUNSET).hi("धनतेरस"));
        FESTIVALS.add(new TithiFest("Diwali", true, 6, 30, When.SUNSET).hi("दीवाली", "दीवाली", "दीपावली"));
        FESTIVALS.add(new TithiFest("Govardhan Puja", false, 7, 1, When.SUNRISE).hi("गोवर्धन पूजा"));
        FESTIVALS.add(new TithiFest("Bhai Dooj", false, 7, 2, When.SUNRISE).hi("भाई दूज", "दूज"));
        FESTIVALS.add(new TithiFest("Chhath Puja", false, 7, 6, When.SUNSET).hi("छठ पूजा", "छठ"));
        FESTIVALS.add(new TithiFest("Guru Nanak Jayanti", true, 7, 15, When.SUNRISE).hi("गुरु नानक जयंती", "नानक"));
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
