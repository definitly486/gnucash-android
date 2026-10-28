package com.crashlytics.android.core;

/** No-op replacement for the discontinued Fabric Crashlytics SDK. */
public class CrashlyticsCore {
    public static class Builder {
        public Builder disabled(boolean disabled) { return this; }
        public CrashlyticsCore build() { return new CrashlyticsCore(); }
    }
}
