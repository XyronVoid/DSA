# Enum names are saved in settings and in the Bin index, so keep them stable across updates.
-keep enum com.spacebachao.app.** { *; }

# Media3 and Play Billing ship their own consumer rules.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
