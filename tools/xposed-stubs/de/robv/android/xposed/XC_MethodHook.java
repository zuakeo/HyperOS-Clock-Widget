package de.robv.android.xposed;

/** 编译期占位，运行时由 LSPosed 提供。 */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            return null;
        }

        public void setResult(Object result) {
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setThrowable(Throwable throwable) {
        }
    }

    /**
     * findAndHookMethod / hookMethod 的返回值。这个名字和嵌套层级必须和运行时一致：
     * ART 解析方法时返回类型也算在签名里，写成 void 会 NoSuchMethodError（踩过）。
     */
    public static class Unhook {
        public void unhook() {
        }
    }
}
