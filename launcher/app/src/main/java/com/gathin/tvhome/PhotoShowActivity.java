package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** The photo screensaver's preview from its settings: the same picture as PhotoDream, and any key ends it. */
public class PhotoShowActivity extends Activity {

    private PhotoShow show;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        show = new PhotoShow(this);
        setContentView(show);
    }

    @Override
    protected void onStart() {
        super.onStart();
        show.start();
    }

    @Override
    protected void onStop() {
        show.stop();
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
