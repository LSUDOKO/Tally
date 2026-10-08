# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.tally.** { *** Companion; }
-keepclasseswithmembers class app.tally.** { kotlinx.serialization.KSerializer serializer(...); }
# BouncyCastle Ed25519 used reflectively by the provider
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# Anthropic Java SDK: Jackson-based models use reflection
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
# Mobile Wallet Adapter
-keep class com.solana.** { *; }
-dontwarn com.solana.**
