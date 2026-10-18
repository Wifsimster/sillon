-dontwarn androidx.media3.**
-keep class androidx.media3.session.** { *; }

-keep class ovh.battistella.sillon.playback.PlaybackService { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
