# media3-session ships no consumer rules, yet it is the module other processes
# (system media controls, Bluetooth, Android Auto) talk to through Bundles and
# AIDL stubs a shrinker can't see being used. Same rule as Ondes.
-dontwarn androidx.media3.**
-keep class androidx.media3.session.** { *; }

# Instantiated by the framework from the manifest, by name.
-keep class ovh.battistella.sillon.playback.PlaybackService { *; }

# kotlinx.serialization, Hilt and OkHttp bring their own consumer rules.

# Strip verbose/debug/info logging from release builds so server addresses and
# stream URLs (which carry the Plex token) never reach logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
