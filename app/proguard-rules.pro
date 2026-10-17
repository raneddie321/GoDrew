# kotlinx.serialization: keep generated serializers for the project format.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.godraw.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.godraw.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.godraw.core.**$$serializer { *; }
-keepclassmembers class com.godraw.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.godraw.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.godraw.app.**$$serializer { *; }

# Google Play Billing
-keep class com.android.vending.billing.** { *; }
