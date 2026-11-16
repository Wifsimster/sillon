-dontwarn **
-dontnote **
-ignorewarnings

-keep class ovh.battistella.sillon.desktop.MainKt { *; }
-keep class ovh.battistella.sillon.desktop.player.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.coroutines.swing.** { *; }
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class androidx.datastore.preferences.** { *; }
-keep class okhttp3.internal.platform.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class coil3.** { *; }
-keep class okio.** { *; }
-keepnames class ** { *; }
