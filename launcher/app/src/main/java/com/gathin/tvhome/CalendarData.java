package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Online data for the calendar popup, layered over what {@link Panchang} computes on the TV:
 * <ul>
 * <li>Tithi with its end time, Purnimanta month, Adhik flag, Vikram Samvat and choghadiya from the free Jagannatha
 * Hora panchang API (jagannathahora.com, no key; one request a day per date, at most one a second).</li>
 * <li>Festivals and public holidays in Hindi from Google's public "Holidays in India" calendar, used for years it
 * covers in full; the computed festivals it lacks (Dhanteras, Lohri ...) are kept.</li>
 * </ul>
 * Answers are cached in the app's files for good (a date's panchang never changes; the holiday calendar is refreshed
 * weekly), so the popup draws at once from the cache and the computation, and fills in as downloads arrive. Any
 * failure leaves the computed values in place and backs off for ten minutes.
 */
final class CalendarData {

    interface Listener {
        /** On the main thread, when downloads changed what {@code month} shows. */
        void onUpdated(YearMonth month);
    }

    private static final String PANCHANG_URL = "https://jagannatha-hora-359167915530.europe-west1.run.app/panchang";
    private static final String HOLIDAYS_URL =
            "https://calendar.google.com/calendar/ical/hi.indian%23holiday%40group.v.calendar.google.com/public/basic.ics";
    private static final long HOLIDAYS_MAX_AGE_MS = 7L * 24 * 3600 * 1000;
    private static final long BACKOFF_MS = 10 * 60 * 1000;
    /** A year with fewer Google entries than this is only partly filled in (2028 on, so far): computed festivals then. */
    private static final int FULL_YEAR = 45;

    private static CalendarData instance;

    private final File dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Bumped for every new refresh, so a download loop for a month no longer shown stops. */
    private final AtomicInteger generation = new AtomicInteger();
    private final Map<LocalDate, JSONObject> panchang = new ConcurrentHashMap<>();
    private final Map<YearMonth, Boolean> loadedMonths = new ConcurrentHashMap<>();
    private volatile Map<LocalDate, List<Holiday>> holidays;
    private volatile Map<Integer, Integer> holidaysPerYear;
    private volatile long offlineUntil;

    private static final class Holiday {
        final String name;
        final boolean publicHoliday;

        Holiday(String name, boolean publicHoliday) {
            this.name = name;
            this.publicHoliday = publicHoliday;
        }
    }

    static synchronized CalendarData get(Context context) {
        if (instance == null) instance = new CalendarData(context.getApplicationContext());
        return instance;
    }

    private CalendarData(Context context) {
        dir = new File(context.getFilesDir(), "calendar");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    // ---- what the popup shows ---------------------------------------------------------------------

    /**
     * The month as computed, with whatever is already cached laid over it. Reads only small files, the first time a
     * month is asked for; the network is left to {@link #refresh}.
     */
    List<Panchang.Day> month(YearMonth month) {
        List<Panchang.Day> days = Panchang.month(month.getYear(), month.getMonthValue());
        loadMonth(month);
        loadMonth(month.plusMonths(1));
        if (holidays == null) loadHolidays();
        for (Panchang.Day day : days) {
            overlayPanchang(day);
            overlayFestivals(day);
        }
        return days;
    }

    /** Choghadiya for a date: the API's when it is cached, otherwise computed. */
    List<Panchang.Period> choghadiya(LocalDate date) {
        JSONObject p = panchang.get(date);
        if (p != null) {
            try {
                List<Panchang.Period> online = parseChoghadiya(p.getJSONObject("choghadiya"), date);
                if (online.size() == 16) return online;
            } catch (Exception ignored) {
                // fall through to the computed ones
            }
        }
        return Panchang.choghadiya(date);
    }

    boolean choghadiyaOnline(LocalDate date) {
        JSONObject p = panchang.get(date);
        return p != null && p.has("choghadiya");
    }

    /** Fetch what {@code month} still lacks, in the background, and tell {@code listener} as it arrives. */
    void refresh(YearMonth month, Listener listener) {
        int gen = generation.incrementAndGet();
        worker.execute(() -> {
            boolean changed = false;
            if (holidaysStale() && online()) changed = fetchHolidays();
            if (changed) post(listener, month, gen);
            LocalDate first = month.atDay(1), last = month.atEndOfMonth().plusDays(1);   // the 1st of next month tells whether the 31st repeats
            int fetched = 0;
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                if (generation.get() != gen || !online()) return;
                if (panchang.containsKey(d)) continue;
                if (!fetchPanchang(d)) return;
                fetched++;
                if (fetched % 7 == 0) post(listener, month, gen);
                SystemClock.sleep(1100);   // the API allows 60 requests a minute per address
            }
            if (fetched > 0) post(listener, month, gen);
        });
    }

    private void post(Listener listener, YearMonth month, int gen) {
        main.post(() -> {
            if (generation.get() == gen) listener.onUpdated(month);
        });
    }

    private boolean online() {
        return System.currentTimeMillis() >= offlineUntil;
    }

    // ---- merging --------------------------------------------------------------------------------------

    private void overlayPanchang(Panchang.Day day) {
        JSONObject p = panchang.get(day.date);
        if (p == null) return;
        try {
            JSONObject tithi = p.getJSONObject("tithi");
            int t = tithi.getInt("number");
            if (t < 1 || t > 30) return;
            JSONObject month = p.getJSONObject("month");
            int m = monthIndex(month.optString("purnimanta"));
            if (m < 0) return;
            day.tithi = t;
            day.month = m;
            day.adhika = month.optBoolean("adhika", false);
            int vs = p.getJSONObject("samvat").optInt("vikrama", 0);
            if (vs > 0) day.samvat = vs;
            LocalDateTime end = parseTime(tithi.optString("end"), day.date);
            day.tithiEnds = end != null ? end : Panchang.tithiEnd(t, day.date);
            // Tomorrow's tithi settles a repeat or a skipped tithi when it is cached; otherwise compute both around
            // the online end time.
            LocalDateTime nextSunrise = Panchang.sunrise(day.date.plusDays(1));
            JSONObject next = panchang.get(day.date.plusDays(1));
            int nextTithi = next != null ? next.getJSONObject("tithi").optInt("number", 0) : 0;
            int following = t % 30 + 1;
            if (nextTithi > 0) {
                day.repeats = nextTithi == t;
                day.skipped = nextTithi == following % 30 + 1 ? following : 0;
            } else {
                day.repeats = day.tithiEnds.isAfter(nextSunrise);
                day.skipped = !day.repeats && Panchang.tithiEndAfter(following, day.tithiEnds).isBefore(nextSunrise) ? following : 0;
            }
            // The API only gives the end of the tithi at sunrise; the skipped one's end is computed.
            day.skippedEnds = day.skipped > 0 ? Panchang.tithiEndAfter(day.skipped, day.tithiEnds) : null;
            day.tithiOnline = true;
        } catch (Exception ignored) {
            // a malformed entry leaves the computed values
        }
    }

    private void overlayFestivals(Panchang.Day day) {
        Map<LocalDate, List<Holiday>> all = holidays;
        Map<Integer, Integer> perYear = holidaysPerYear;
        if (all == null || perYear == null) return;
        Integer count = perYear.get(day.date.getYear());
        if (count == null || count < FULL_YEAR) return;
        List<String> names = new ArrayList<>();
        boolean holiday = false;
        List<Holiday> today = all.get(day.date);
        if (today != null) {
            for (Holiday h : today) {
                names.add(h.name);
                holiday |= h.publicHoliday;
            }
        }
        // Keep computed festivals Google does not list near that date (it has no Dhanteras or Lohri).
        for (Panchang.Fest f : day.fests) {
            if (!listedNear(f, day.date, all)) {
                names.add(f.hindi);
                holiday |= f.holiday;
            }
        }
        day.festival = String.join(" · ", names);
        day.holiday = holiday;
        day.festivalOnline = true;
    }

    /** Whether Google lists this festival within two days of the computed date, which may differ by one. */
    private static boolean listedNear(Panchang.Fest f, LocalDate date, Map<LocalDate, List<Holiday>> all) {
        for (int offset = -2; offset <= 2; offset++) {
            List<Holiday> list = all.get(date.plusDays(offset));
            if (list == null) continue;
            for (Holiday h : list) {
                for (String word : f.match) {
                    if (h.name.contains(word)) return true;
                }
            }
        }
        return false;
    }

    private static int monthIndex(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String[] keys = {"chai", "vai", "jye", "asha", "shra", "bha", "ashw", "kart", "marg", "pau", "magh", "phal"};
        // "ashw" before "asha": both start with "ash".
        if (n.startsWith("ashw") || n.startsWith("asv") || n.startsWith("ashv")) return 6;
        for (int i = 0; i < keys.length; i++) {
            if (i != 6 && n.startsWith(keys[i])) return i;
        }
        if (n.startsWith("sra")) return 4;
        return -1;
    }

    /** "11:29:05" or "02:26:33 (+1d)" on the request date, IST. */
    static LocalDateTime parseTime(String text, LocalDate date) {
        if (text == null || text.isEmpty()) return null;
        try {
            String s = text.trim();
            int days = 0;
            int plus = s.indexOf("(+");
            if (plus >= 0) {
                String n = s.substring(plus + 2).replaceAll("[^0-9]", "");
                days = n.isEmpty() ? 1 : Integer.parseInt(n);
                s = s.substring(0, plus).trim();
            }
            return LocalDateTime.of(date.plusDays(days), LocalTime.parse(s.length() == 5 ? s + ":00" : s));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Panchang.Period> parseChoghadiya(JSONObject c, LocalDate date) throws Exception {
        List<Panchang.Period> periods = new ArrayList<>();
        LocalDateTime previous = null;
        for (String part : new String[]{"day", "night"}) {
            JSONArray list = c.getJSONArray(part);
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                int kind = choghadiyaIndex(o.optString("name"));
                if (kind < 0) return new ArrayList<>();
                // Times are clock times; the night runs past midnight into the next date.
                LocalDateTime start = LocalDateTime.of(date, LocalTime.parse(o.getString("start")));
                if (previous != null && start.isBefore(previous.minusHours(1))) start = start.plusDays(1);
                if (previous != null && start.isBefore(previous)) start = previous;
                LocalDateTime end = LocalDateTime.of(start.toLocalDate(), LocalTime.parse(o.getString("end")));
                if (end.isBefore(start)) end = end.plusDays(1);
                periods.add(new Panchang.Period(kind, part.equals("night"), start, end));
                previous = end;
            }
        }
        return periods;
    }

    private static int choghadiyaIndex(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("udv")) return 0;
        if (n.startsWith("cha")) return 1;
        if (n.startsWith("lab")) return 2;
        if (n.startsWith("amr")) return 3;
        if (n.startsWith("kaa") || n.startsWith("kal")) return 4;
        if (n.startsWith("shu")) return 5;
        if (n.startsWith("rog")) return 6;
        return -1;
    }

    // ---- cache and network ------------------------------------------------------------------------------

    private File monthFile(YearMonth m) {
        return new File(dir, String.format(Locale.ROOT, "panchang-%d-%02d.json", m.getYear(), m.getMonthValue()));
    }

    private void loadMonth(YearMonth m) {
        if (loadedMonths.putIfAbsent(m, Boolean.TRUE) != null) return;
        File f = monthFile(m);
        if (!f.exists()) return;
        try {
            JSONObject all = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) {
                String key = it.next();
                panchang.put(LocalDate.parse(key), all.getJSONObject(key));
            }
        } catch (Exception ignored) {
            // a damaged cache is downloaded again
        }
    }

    private synchronized void saveMonth(YearMonth m) {
        try {
            JSONObject all = new JSONObject();
            for (Map.Entry<LocalDate, JSONObject> e : panchang.entrySet()) {
                if (YearMonth.from(e.getKey()).equals(m)) all.put(e.getKey().toString(), e.getValue());
            }
            write(monthFile(m), all.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // kept in memory for this session
        }
    }

    private boolean fetchPanchang(LocalDate d) {
        try {
            JSONObject body = new JSONObject()
                    .put("date", d.toString())
                    .put("latitude", Panchang.LATITUDE)
                    .put("longitude", Panchang.LONGITUDE)
                    .put("timezone", 5.5)
                    .put("place", "New Delhi");
            byte[] answer = http(PANCHANG_URL, body.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject p = new JSONObject(new String(answer, StandardCharsets.UTF_8)).getJSONObject("panchang");
            // Keep only what the calendar reads.
            JSONObject keep = new JSONObject()
                    .put("tithi", p.getJSONObject("tithi"))
                    .put("month", p.getJSONObject("month"))
                    .put("samvat", p.getJSONObject("samvat"))
                    .put("choghadiya", p.getJSONObject("choghadiya"));
            loadMonth(YearMonth.from(d));
            panchang.put(d, keep);
            saveMonth(YearMonth.from(d));
            return true;
        } catch (Exception e) {
            offlineUntil = System.currentTimeMillis() + BACKOFF_MS;
            return false;
        }
    }

    private File holidaysFile() {
        return new File(dir, "holidays-in-hi.ics");
    }

    private boolean holidaysStale() {
        File f = holidaysFile();
        return !f.exists() || System.currentTimeMillis() - f.lastModified() > HOLIDAYS_MAX_AGE_MS;
    }

    private boolean fetchHolidays() {
        try {
            byte[] ics = http(HOLIDAYS_URL, null);
            String text = new String(ics, StandardCharsets.UTF_8);
            if (!text.contains("BEGIN:VCALENDAR")) throw new IllegalStateException("not a calendar");
            write(holidaysFile(), ics);
            parseHolidays(text);
            return true;
        } catch (Exception e) {
            offlineUntil = System.currentTimeMillis() + BACKOFF_MS;
            return false;
        }
    }

    private void loadHolidays() {
        File f = holidaysFile();
        if (!f.exists()) {
            holidays = new ConcurrentHashMap<>();
            holidaysPerYear = new ConcurrentHashMap<>();
            return;
        }
        try {
            parseHolidays(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            holidays = new ConcurrentHashMap<>();
            holidaysPerYear = new ConcurrentHashMap<>();
        }
    }

    /** VEVENTs with an all-day DTSTART and a SUMMARY; "सार्वजनिक छुट्टी" (public holiday) in the description. */
    private void parseHolidays(String text) {
        Map<LocalDate, List<Holiday>> map = new ConcurrentHashMap<>();
        Map<Integer, Integer> perYear = new ConcurrentHashMap<>();
        String unfolded = text.replace("\r\n ", "").replace("\n ", "");
        for (String event : unfolded.split("BEGIN:VEVENT")) {
            String start = field(event, "DTSTART;VALUE=DATE:");
            String summary = field(event, "SUMMARY:");
            if (start == null || summary == null || start.length() < 8) continue;
            LocalDate date;
            try {
                date = LocalDate.of(Integer.parseInt(start.substring(0, 4)), Integer.parseInt(start.substring(4, 6)),
                        Integer.parseInt(start.substring(6, 8)));
            } catch (Exception e) {
                continue;
            }
            String description = field(event, "DESCRIPTION:");
            boolean publicHoliday = description != null
                    && (description.startsWith("सार्वजनिक") || description.startsWith("Public holiday"));
            String name = summary.replace("\\,", ",").replace("/ ", "/").trim();
            List<Holiday> list = map.get(date);
            if (list == null) map.put(date, list = new ArrayList<>());
            list.add(new Holiday(name, publicHoliday));
            perYear.merge(date.getYear(), 1, Integer::sum);
        }
        // Public holidays first, so the cell's one line leads with the day off.
        for (List<Holiday> list : map.values()) list.sort((a, b) -> Boolean.compare(b.publicHoliday, a.publicHoliday));
        holidays = map;
        holidaysPerYear = perYear;
    }

    private static String field(String event, String key) {
        int i = event.indexOf("\n" + key);
        if (i < 0) return null;
        int start = i + 1 + key.length();
        int end = event.indexOf('\n', start);
        return (end < 0 ? event.substring(start) : event.substring(start, end)).replace("\r", "");
    }

    private static byte[] http(String url, byte[] post) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        if (post != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(post);
            }
        }
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) bytes.write(buffer, 0, n);
                return bytes.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static void write(File f, byte[] data) throws Exception {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) throw new IllegalStateException("rename failed");
    }
}
