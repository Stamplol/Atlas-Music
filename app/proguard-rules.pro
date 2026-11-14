# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# Media3
-keep class androidx.media3.** { *; }

# Chaquopy / Python
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep enum names for Parcelable status values
-keepclassmembers enum com.atlas.music.downloads.** { *; }

# Chaquopy reflection entry points for the downloader progress bridge
-keep class com.atlas.music.downloads.AtlasProgressCallbacks { *; }
-keep interface com.atlas.music.downloads.AtlasProgressCallbacks$* { *; }
-keep class com.atlas.music.downloads.PythonBridge { *; }
-keep interface com.atlas.music.downloads.* { *; }

# AppWidget providers are instantiated via reflection by the system
-keep class com.atlas.music.widget.* { *; }

# MediaSession custom command arg access
-keepclassmembers class * { @androidx.media3.session.* <methods>; }
