package de.robv.android.xposed;

/** 编译期占位，运行时由 LSPosed 提供。 */
public final class XposedHelpers {
    public static void findAndHookMethod(String className, ClassLoader classLoader,
                                         String methodName, Object... args) {
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }
}
