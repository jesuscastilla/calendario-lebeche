# Reglas de ProGuard/R8 (se aplicarán cuando se active minifyEnabled).

# biweekly (iCalendar)
-keep class net.sf.biweekly.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
