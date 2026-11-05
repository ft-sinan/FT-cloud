# Keep class & method names transparent (unobfuscated) while R8 shrinks unused code/resources.
# Prevents Google Play Protect on-device ML false positives on open-source sideloaded APKs
# that legitimately talk to api.telegram.org and support self-updates.
-dontobfuscate
-keepattributes *Annotation*, InnerClasses, SourceFile, LineNumberTable
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep FT-Cloud serializable DTOs and manifest models
-keep,includedescriptorclasses class com.ftcloud.app.**$$serializer { *; }
-keepclassmembers class com.ftcloud.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ftcloud.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink / Security Crypto (used by EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.zxing.**
