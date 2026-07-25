# MapMonarchs R8 / ProGuard keep rules.
#
# NOT ACTIVE YET. The release build sets optimization.enable = false, so R8 does
# not run and these rules are inert. They are written and committed so that
# turning R8 on later is a one-line change plus a verification pass, not a
# research task -- see the comment on the release buildType in build.gradle.kts.
#
# WHY EACH RULE EXISTS: the app reads server rows with kotlinx.serialization,
# which resolves a generated $$serializer for each @Serializable class
# REFLECTIVELY at runtime. R8 sees no direct reference to those serializers and
# strips them, and the failure surfaces only when a network read actually
# deserializes -- a path gated behind sign-in and therefore easy to miss in
# testing. These rules keep exactly the reflective surface and nothing more.

# ---- kotlinx.serialization (official pattern) ------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the generated serializers and the serializer() factory for every
# @Serializable type in this app (the DTOs the profile/territory reads decode).
-keepclassmembers @kotlinx.serialization.Serializable class com.mapmonarchs.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mapmonarchs.**$$serializer { *; }

# ---- Enums used across serialization / when() -------------------------------
-keepclassmembers enum com.mapmonarchs.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Supabase (ktor/kotlinx), Mapbox, and Play Services ship their own consumer
# ProGuard rules via their AARs, so no app-side keeps are needed for them. If a
# release-with-R8 verification pass surfaces a missing class from one of those,
# add the specific keep here rather than a broad -keep class **.
