# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.ncmcloud.player.**$$serializer { *; }
-keepclassmembers class com.ncmcloud.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.ncmcloud.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager / Room（R8 会删掉 WorkDatabase_Impl 构造方法）
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.WorkManagerInitializer { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.** { *; }
-keep class androidx.startup.** { *; }
