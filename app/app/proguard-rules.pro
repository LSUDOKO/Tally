# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.tally.** { *** Companion; }
-keepclasseswithmembers class app.tally.** { kotlinx.serialization.KSerializer serializer(...); }
# BouncyCastle Ed25519 used reflectively by the provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# Mobile Wallet Adapter
-keep class com.solana.** { *; }
-dontwarn com.solana.**
# R8 full mode drops no-arg constructors that libraries call by reflection; found by launching the
# release APK on a phone (crash at start, then ML Kit unable to build its barcode scanner).
# WorkManager (used by the Glance widget) instantiates its Room database implementation by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
# ML Kit discovers its components through Firebase's ComponentRegistrar, also by reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
