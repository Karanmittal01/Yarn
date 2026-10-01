# SQLCipher: native code calls back into these classes by name.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# MediaPipe LLM inference uses JNI and protobuf reflection.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ML Kit / Play services ship their own consumer rules; silence optional deps.
-dontwarn com.google.android.gms.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.auto.value.**

# kotlinx.serialization (backup format, navigation routes)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.yarn.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.yarn.**$$serializer { *; }

# Keep line numbers for useful crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
