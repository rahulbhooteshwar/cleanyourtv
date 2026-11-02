# My TV

Sony BRAVIA 4K VH2 (`BRAVIA_VH2_PA`, India firmware, Android 12) at `192.168.1.8`. Commands run from the repo root
in PowerShell.

| What | Command |
|---|---|
| Rebuild and install the launcher | `powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.8 -StockLauncher none -HomeKeyService -RestoreKeyServices` |
| Debloat (again, after a firmware update) | `powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.8 my-tv\debloat.txt` |
| Undo the debloat | `powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.8 my-tv\debloat.txt com.google.android.apps.tv.launcherx` |
| Theme | `powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.8 my-tv\theme-glass` |
| Photos screensaver pictures | put them in `addons\photo-screensaver\pictures\`, then `addons\photo-screensaver\sync.ps1 -TvHost 192.168.1.8` |

The stock Google TV launcher (`launcherx`) stays **enabled**: Google Assistant opens its results (search, "open X",
"play Y") through it, and with it disabled every such request ends in "There's a problem with Google Assistant"
(logcat: `Failed to execute provider.OPEN`). adb may not disable just its Home activities on this firmware, so it keeps
Android's Home, and the launcher's Home-key helper (`-HomeKeyService`, `stock_home_redirect` in config.xml) sends its
home screen straight back to ours. `-StockLauncher none` keeps the install script from disabling it again.

Keep `-RestoreKeyServices` on the launcher install: it grants `WRITE_SECURE_SETTINGS`, which the **Screensaver**
menu (hold OK on Settings) needs to switch screensavers. Google TV's own settings can't pick a non-Google one.

Customizations in the launcher: six-app dock, Sony's tuner as Live TV, the Calendar pill (Vikram Samvat tithis and
Indian holidays, `Panchang.java` / `CalendarPanel.java`) and the Screensaver menu. Add-ons installed: Photos
(`addons/photo-screensaver`) and Digital Rain (`addons/digital-rain`).
