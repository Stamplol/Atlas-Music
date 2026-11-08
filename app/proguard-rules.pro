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
