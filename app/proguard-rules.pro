# FluxPDF ProGuard / R8 Rules

# Preserve JNI bridge class and native method signatures for Rust libfluxpdf_engine.so
-keep class com.example.engine.FluxNativeBridge {
    public static native <methods>;
    *;
}

# Preserve Room entities and generated database classes
-keep class com.example.data.** { *; }
