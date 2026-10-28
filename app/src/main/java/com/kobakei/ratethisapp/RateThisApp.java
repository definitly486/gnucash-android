package com.kobakei.ratethisapp;

import android.content.Context;

/** No-op replacement for the RateThisApp library, which is no longer published. */
public class RateThisApp {
    public static class Config {
        public Config(int criteriaInstallDays, int criteriaLaunchTimes) { }
    }
    public static void init(Config config) { }
    public static void onStart(Context context) { }
    public static boolean showRateDialogIfNeeded(Context context) { return false; }
}
