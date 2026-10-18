package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

/** Opens the TCL TV app on its OTA tuner, preserving TCL's scanned channel database. */
final class LiveTv {
    private static final String TCL_TUNER_PACKAGE = "com.tcl.tvinput";

    private LiveTv() {}

    static String findTunerInputId(Context context) {
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (manager == null) return null;
        for (TvInputInfo input : manager.getTvInputList()) {
            if (input.getType() == TvInputInfo.TYPE_TUNER
                    && TCL_TUNER_PACKAGE.equals(input.getServiceInfo().packageName)) {
                return input.getId();
            }
        }
        return null;
    }

    static boolean open(Context context) {
        String inputId = findTunerInputId(context);
        if (inputId == null) return false;
        String tvPackage = context.getString(R.string.live_tv_package).trim();
        Intent intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(tvPackage);
        if (intent == null) return false;
        intent.putExtra("inputId", inputId);
        intent.putExtra("enterSource", "LiveTv");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
