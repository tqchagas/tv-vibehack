package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Intercepts only the TCL remote's dedicated YouTube key. */
public final class RemoteShortcutService extends AccessibilityService {
    private static final int YOUTUBE_SCAN_CODE = 569;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service does not inspect window or screen content.
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        boolean youtubeKey = event.getKeyCode() == KeyEvent.KEYCODE_TV_SATELLITE_CS
                || event.getScanCode() == YOUTUBE_SCAN_CODE;
        if (!youtubeKey) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Intent launch = getPackageManager()
                    .getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv");
            if (launch == null) {
                launch = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube.tv");
            }
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
            }
        }
        return true;
    }
}
