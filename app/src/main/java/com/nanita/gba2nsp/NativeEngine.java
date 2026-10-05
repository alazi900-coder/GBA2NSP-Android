package com.nanita.gba2nsp;

public final class NativeEngine {
    static {
        try { System.loadLibrary("gba2nsp"); } catch (Throwable ignored) {}
    }
    private NativeEngine() {}
    public static native boolean nativePackerAvailable();
    public static native String nativeEngineVersion();
    public static native int nativeBuildNsp(String keysetPath, String workspacePath, String outputDirPath);
}
