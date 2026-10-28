package com.crashlytics.android;

import com.crashlytics.android.core.CrashlyticsCore;

/** No-op replacement for the discontinued Fabric Crashlytics SDK. */
public class Crashlytics {
    public static void log(String msg) { }
    public static void logException(Throwable t) { }
    public static void start(android.content.Context context) { }

    public static class Builder {
        public Builder core(CrashlyticsCore core) { return this; }
        public Crashlytics build() { return new Crashlytics(); }
    }
}
