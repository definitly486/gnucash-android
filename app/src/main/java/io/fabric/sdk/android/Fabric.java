package io.fabric.sdk.android;

/** No-op replacement for the discontinued Fabric SDK. */
public class Fabric {
    public static Fabric with(android.content.Context context, Object... kits) {
        return new Fabric();
    }
}
