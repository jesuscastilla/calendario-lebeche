# Reglas de ProGuard/R8 (se aplicarán cuando se active minifyEnabled).

# biweekly (iCalendar)
-keep class biweekly.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# biweekly: modulo JSON opcional (Jackson no incluido)
-dontwarn com.fasterxml.jackson.**

# Mantener atributos para bibliotecas con reflexion (biweekly)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*