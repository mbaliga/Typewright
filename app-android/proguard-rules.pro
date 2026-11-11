# R8 keep rules for the release build (docs/release/RELEASE_CHECKLIST.md). Only what is reached by name
# rather than by a call R8 can see needs a rule here; everything else may be shrunk and renamed.
# `tools/release/android_smoke.py` checks the APK this produces, and a release build opened on a real
# phone is owner-verified only (CLAUDE.md law 4).

# Stack traces in a crash report the person copies are readable lines, not mangled names. The
# mapping file (build/outputs/mapping/release/mapping.txt) stays with the maintainer.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: serializers are found by name at run time. The library ships its own consumer
# rules; these are the ones for classes this app marks @Serializable.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.asoc.typewright.** {
    *** Companion;
}
-keepclasseswithmembers class com.asoc.typewright.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# xmlutil (the UFO and glif reader) looks its platform implementation up with the service loader.
-keep class nl.adaptivity.xmlutil.** { *; }
-keep class io.github.pdvrieze.** { *; }

# The shared modules are compiled for desktop and the web too, and name classes that Android does not have.
# R8 only reaches them from code that Android never runs.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.management.**

# qa's ExternalProcess stops a child process tree with ProcessHandle, which Android does not have. Only the
# desktop's layer-one check starts external processes; Android never reaches it (it has no Python).
-dontwarn java.lang.ProcessHandle
