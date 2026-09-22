# OpenCV is loaded from the Maven artifact. Keep its public JNI entry points
# when an optimized release build is enabled.
-keep class org.opencv.** { *; }