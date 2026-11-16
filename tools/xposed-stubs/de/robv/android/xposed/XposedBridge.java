package de.robv.android.xposed;

/** 编译期占位，运行时由 LSPosed 提供。 */
public final class XposedBridge {
    public static void log(String text) {
    }

    public static void log(Throwable throwable) {
    }
}
