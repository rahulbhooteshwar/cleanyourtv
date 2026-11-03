# My TV

Sony BRAVIA 4K VH2 (`BRAVIA_VH2_PA`, India firmware, Android 12) at `172.20.10.9`. Commands run from the repo root
in PowerShell.

| What | Command |
|---|---|
| Rebuild and install the launcher | `powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 172.20.10.9 -StockLauncher none -HomeKeyService -RestoreKeyServices` |
| Debloat (again, after a firmware update) | `powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 172.20.10.9 my-tv\debloat.txt` |
| Undo the debloat | `powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 172.20.10.9 my-tv\debloat.txt com.google.android.apps.tv.launcherx` |
| Theme (run `my-tv\make-wallpapers.ps1` first: it builds the gitignored `wallpapers/` collection) | `powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 172.20.10.9 my-tv\theme-glass` |
| Pick or install a theme on the TV | Hold OK on Settings > Launcher settings > Theme: installed themes ("My theme" is the glass setup pushed from the PC), then the CleanYourTV repo's themes, installed on pick (their dock, columns and hidden apps are left out). Library: `Android/data/com.gathin.tvhome/files/themes/` |
| App colour glow behind the wallpaper | Launcher settings > App colour glow: On/Off (kept across theme changes) |
| Wallpaper (Settings menu, hold OK > Wallpaper) | Wallpaper On/Off; Pictures: tick any number in a thumbnail grid - "Theme default" (the theme's own pictures, untickable) plus pictures in the folder; Folder: Pictures/Wallpapers or any folder with pictures; Change every Off / 3 s / 5 s / 15 min / 30 min / 1 h / 3 h (crossfade). Applying a theme ticks its pictures again; folder ticks stay |
| Your own wallpapers / screensaver photos and audio | Put them in `Pictures/Wallpapers`, `Pictures/Screensaver` (or any folder under Pictures, DCIM, Download, a USB drive) and `Music` with X-plore. Needs once: `pm grant com.gathin.tvhome android.permission.READ_EXTERNAL_STORAGE` |
| Photo screensaver (built into the launcher) | Settings (hold OK) > Screensaver > Photo screensaver settings: folder, all or picked photos, change every 3 s - 5 min, clock and small calendar on/off and their corner (or centre), background audio (a folder or a track) and its own volume, Preview, Use as screensaver. The Photos and Digital Rain add-ons were uninstalled; Clock, Colors and Backdrop remain |

The stock Google TV launcher (`launcherx`) stays **enabled**: Google Assistant opens its results (search, "open X",
"play Y") through it, and with it disabled every such request ends in "There's a problem with Google Assistant"
(logcat: `Failed to execute provider.OPEN`). adb may not disable just its Home activities on this firmware, so it keeps
Android's Home, and the launcher's Home-key helper (`-HomeKeyService`, `stock_home_redirect` in config.xml) sends its
home screen straight back to ours. `-StockLauncher none` keeps the install script from disabling it again.

**Google TV home on demand.** Hold OK on Settings > *Switch to Google TV home*: the redirect stands down and the stock
home (with the Sony tab, `com.sony.dtv.sonytab`, and `launcherxcustomizer`, both re-enabled) is Home. To come back, open
the **Home** app from Google TV's apps row. sonytab and launcherxcustomizer are kept from running in the background
either way (they work normally on screen):

```
for p in com.sony.dtv.sonytab com.sony.dtv.launcherxcustomizer; do cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; cmd appops set $p RUN_IN_BACKGROUND ignore; am set-standby-bucket $p restricted; done
```
(`allow` instead of `ignore` lifts it.) launcherx itself is **not** restricted: with RUN_ANY_IN_BACKGROUND ignored its
HeadsUpAlertService (notification pop-ups) crashed with ForegroundServiceDidNotStartInTimeException, so it was set
back to `allow` on 2026-11-02.

The calendar pill shows Hindi (Devanagari) tithis, months and Vikram Samvat, and OK on a day opens its choghadiya.
Tithis with end times, the month, samvat and choghadiya come from the free Jagannatha Hora API (one request per date,
at most one a second, cached in the app's files for good); festivals and holidays from Google's public Hindi "Holidays
in India" calendar (refreshed weekly). Without a network, or for dates not cached yet, everything is computed on the TV
(`Panchang.java`), so the popup never waits; downloads fill it in as they arrive. Combined (kshaya) tithis show as
"à¤•à¥ƒà¤·à¥à¤£ à¥«/à¥¬", and the line under the grid gives each tithi's end time. This gives the launcher the INTERNET permission.

Screensaver menu > Start after (1-60 min idle) writes `screen_off_timeout`; it needs this once (survives reinstalls):
`tools\venv\Scripts\python.exe -I tools\tv.py "appops set com.gathin.tvhome WRITE_SETTINGS allow"`.

Keep `-RestoreKeyServices` on the launcher install: it grants `WRITE_SECURE_SETTINGS`, which the **Screensaver**
menu (hold OK on Settings) needs to switch screensavers. Google TV's own settings can't pick a non-Google one.

Customizations in the launcher: six-app dock, Sony's tuner as Live TV, the Calendar pill (Vikram Samvat tithis and
Indian holidays, `Panchang.java` / `CalendarPanel.java`) and the Screensaver menu. Add-ons installed: Photos
(`addons/photo-screensaver`) and Digital Rain (`addons/digital-rain`).

Clock: the TV has no clock that keeps running without power. After a power cut or a full restart it starts at its
firmware build date (2026-06-05) until it can reach a time server over the internet, then corrects itself. Turning it
off with the remote (standby) keeps the time. If it has to run offline after a restart, set the time in Settings >
System > Date & time: turn off Automatic date & time, set the date and time, and turn automatic back on later.
The time zone is set manually to India (Asia/Kolkata), so it never depends on the network.
