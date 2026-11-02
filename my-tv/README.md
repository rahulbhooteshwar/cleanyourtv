# My TV

Sony BRAVIA 4K VH2 (`BRAVIA_VH2_PA`, India firmware, Android 12) at `192.168.1.8`. Commands run from the repo root
in PowerShell.

| What | Command |
|---|---|
| Rebuild and install the launcher | `powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.8 -StockLauncher none -HomeKeyService -RestoreKeyServices` |
| Debloat (again, after a firmware update) | `powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.8 my-tv\debloat.txt` |
| Undo the debloat | `powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.8 my-tv\debloat.txt com.google.android.apps.tv.launcherx` |
| Theme (run `my-tv\make-wallpapers.ps1` first: it builds the gitignored `wallpapers/` collection) | `powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.8 my-tv\theme-glass` |
| Pick a wallpaper on the TV | Hold OK on Settings > Launcher settings > Wallpaper (38 pictures; "Theme default" goes back to glass) |
| Photos screensaver pictures | put them in `addons\photo-screensaver\pictures\`, then `addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.8` |

The stock Google TV launcher (`launcherx`) stays **enabled**: Google Assistant opens its results (search, "open X",
"play Y") through it, and with it disabled every such request ends in "There's a problem with Google Assistant"
(logcat: `Failed to execute provider.OPEN`). adb may not disable just its Home activities on this firmware, so it keeps
Android's Home, and the launcher's Home-key helper (`-HomeKeyService`, `stock_home_redirect` in config.xml) sends its
home screen straight back to ours. `-StockLauncher none` keeps the install script from disabling it again.

**Google TV home on demand.** Hold OK on Settings > *Switch to Google TV home*: the redirect stands down and the stock
home (with the Sony tab, `com.sony.dtv.sonytab`, and `launcherxcustomizer`, both re-enabled) is Home. To come back, open
the **Home** app from Google TV's apps row. launcherx, sonytab and launcherxcustomizer are kept from running in the
background either way (they work normally on screen, and Assistant still opens results through launcherx):

```
for p in com.google.android.apps.tv.launcherx com.sony.dtv.sonytab com.sony.dtv.launcherxcustomizer; do cmd appops set $p RUN_ANY_IN_BACKGROUND ignore; cmd appops set $p RUN_IN_BACKGROUND ignore; am set-standby-bucket $p restricted; done
```
(`allow` instead of `ignore` lifts it.) Android keeps launcherx in its exempt standby bucket as the system Home, so for it
only the background op applies.

The calendar pill shows Hindi (Devanagari) tithis, months and Vikram Samvat, and OK on a day opens its choghadiya.
Tithis with end times, the month, samvat and choghadiya come from the free Jagannatha Hora API (one request per date,
at most one a second, cached in the app's files for good); festivals and holidays from Google's public Hindi "Holidays
in India" calendar (refreshed weekly). Without a network, or for dates not cached yet, everything is computed on the TV
(`Panchang.java`), so the popup never waits; downloads fill it in as they arrive. Combined (kshaya) tithis show as
"कृष्ण ५/६", and the line under the grid gives each tithi's end time. This gives the launcher the INTERNET permission.

Screensaver menu > Start after (1-60 min idle) writes `screen_off_timeout`; it needs this once (survives reinstalls):
`tools\venv\Scripts\python.exe -I tools\tv.py "appops set com.gathin.tvhome WRITE_SETTINGS allow"`.

Keep `-RestoreKeyServices` on the launcher install: it grants `WRITE_SECURE_SETTINGS`, which the **Screensaver**
menu (hold OK on Settings) needs to switch screensavers. Google TV's own settings can't pick a non-Google one.

Customizations in the launcher: six-app dock, Sony's tuner as Live TV, the Calendar pill (Vikram Samvat tithis and
Indian holidays, `Panchang.java` / `CalendarPanel.java`) and the Screensaver menu. Add-ons installed: Photos
(`addons/photo-screensaver`) and Digital Rain (`addons/digital-rain`).
