package de.robv.android.xposed.callbacks;

/** 编译期占位，运行时由 LSPosed 提供。 */
public final class XC_LoadPackage {
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
