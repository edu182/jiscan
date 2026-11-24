# Keep Tesseract JNI classes
-keep class com.googlecode.tesseract.android.** { *; }
-keep class net.sourceforge.lept4j.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
