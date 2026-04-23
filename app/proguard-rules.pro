# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-keep class com.shogun.speedtest.** { *; }

# Gson: @SerializedName アノテーションを保持
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# speedtest-android モデルクラス（JSONパース対象）
-keep class com.shogun.speedtest.update.UpdateInfo { *; }
-keep class com.shogun.speedtest.data.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# Shizuku helper classes must retain method names used by the release build.
-keep class rikka.shizuku.** { *; }
