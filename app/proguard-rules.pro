# kotlinx-serialization (SavedHost, settings JSON) — keep generated serializers.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepnames @kotlinx.serialization.Serializable class *
-keepclassmembers class * {
    @kotlinx.serialization.SerialInfo <fields>;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# dnsjava uses reflection for record types.
-keep class org.xbill.DNS.** { *; }
# dnsjava references JDK-internal name-service SPI absent on Android.
-dontwarn sun.net.spi.nameservice.**
-dontwarn org.xbill.DNS.spi.**
# dnsjava optionally uses JNA (Windows resolver config) — absent, unused.
-dontwarn com.sun.jna.**
# dnsjava optionally uses JNDI + Lombok/SLF4J compile-time bits — absent, unused.
-dontwarn javax.naming.**
-dontwarn lombok.**
-dontwarn org.slf4j.impl.**

# OkHttp / Okio ship their own rules; keep public API just in case.
-keepnames class okhttp3.** { *; }
-keepnames class okio.** { *; }
