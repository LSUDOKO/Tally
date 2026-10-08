# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.tally.** { *** Companion; }
-keepclasseswithmembers class app.tally.** { kotlinx.serialization.KSerializer serializer(...); }
# BouncyCastle Ed25519 used reflectively by the provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
