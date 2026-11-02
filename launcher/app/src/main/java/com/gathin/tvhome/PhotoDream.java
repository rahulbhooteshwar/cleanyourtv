package com.gathin.tvhome;

import android.service.dreams.DreamService;

/**
 * Screensaver: your photos, with an optional clock, small calendar and background audio (PhotoShow), set up in the
 * Screensaver menu (PhotoDreamSettingsActivity). Built into the launcher, so no add-on app is needed.
 */
public class PhotoDream extends DreamService {

    private PhotoShow show;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        show = new PhotoShow(this);
        setContentView(show);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        show.start();
    }

    @Override
    public void onDreamingStopped() {
        show.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (show != null) show.stop();
        super.onDetachedFromWindow();
    }
}
