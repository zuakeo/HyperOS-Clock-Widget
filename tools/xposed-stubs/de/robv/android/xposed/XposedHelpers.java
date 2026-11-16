package de.robv.android.xposed;

/**
 * 编译期占位，运行时由 LSPosed 提供。
 *
 * ⚠️ 签名必须和运行时**完全一致**（包括返回类型）：ART 解析 invoke-static 时
 * 返回类型属于方法签名的一部分。这里如果写成 void，调用会直接抛
 * `NoSuchMethodError: No static method findAndHookMethod(...)V`
 * ——v1.6 的状态栏时钟一直没生效就是这个原因（编译桩写错了，hook 一个都没挂上）。
 * 运行时实测（Redmi K Pad / Android 17，LSPosed）：
 *   XC_MethodHook$Unhook findAndHookMethod(String, ClassLoader, String, Object...)
 *   XC_MethodHook$Unhook findAndHookMethod(Class, String, Object...)
 *   XC_MethodHook$Unhook findAndHookConstructor(String, ClassLoader, Object...)
 */
public final class XposedHelpers {
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader,
                                                         String methodName, Object... args) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName,
                                                         Object... args) {
        return null;
    }

    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader,
                                                              Object... args) {
        return null;
    }

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
    }
}
