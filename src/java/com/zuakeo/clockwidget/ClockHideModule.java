package com.zuakeo.clockwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 本 app 自带的 LSPosed 模块：**桌面当前这一页**有本应用的时钟组件时，把状态栏的时间藏掉。
 *
 * 作用域只需要「系统界面」。桌面那一半由 app 自己的无障碍服务（PageWatchService）算，
 * 结果写在 ClockFlag 组件的启用状态里，模块只读——跨包读不需要权限。
 *
 * 为什么不在桌面进程里 hook：HyperOS 4 的桌面是 Flutter 实现，APK 里没有 dex；
 * LSPosed 2.x 对它的「HyperOS Runtime」只加载原生 .so 模块（日志里只有 HyperCeiler /
 * HomeTweaks 的 native module），普通 dex 模块根本注入不进去——实测过。
 *
 * ── 其它踩过的坑 ────────────────────────────────────────
 * 1. hook 必须反射调：ART 解析 invoke-static 时**返回类型也算签名**，
 *    findAndHookMethod 官方返回 XC_MethodHook.Unhook，v1.6 的编译桩写成 void，
 *    每个调用都抛 NoSuchMethodError（被 catch 吞了，日志里只见「hook 失败」）。
 * 2. SystemUI 里读不到别人的组件列表（SecurityException），所以只能读组件启用状态。
 * 3. 状态栏左上角是两个控件：clock=StatusBarClock=时间，pad_clock=MiuiClock=日期。
 *    只藏 clock；pad_clock 是日期，藏了日期就没了（踩过）。
 * 4. 设一次 GONE 会被 MIUI 改回 VISIBLE，所以在 View#setVisibility 的 before hook 里改参数。
 */
public class ClockHideModule implements IXposedHookLoadPackage {

    private static final String PKG = "com.zuakeo.clockwidget";
    /** 与 ClockFlag.COMPONENT 一致。 */
    private static final String FLAG = PKG + ".ClockFlag";

    private static final String MIUI_CLOCK = "com.android.systemui.statusbar.views.MiuiClock";
    private static final String POLICY_CLOCK = "com.android.systemui.statusbar.policy.Clock";
    private static final String[] STATUS_BAR_IDS = {
            "status_bar_contents", "phone_status_bar_left_container",
    };
    private static final String[] NOT_STATUS_BAR_IDS = {
            "keyguard_status_bar_content", "notification_panel", "shade_header_container",
    };
    /** 状态栏里显示时间的视图 id（clock=时间，pad_clock=日期，别搞混）。 */
    private static final String[] TIME_IDS = {"clock"};

    private static Class<?> clockClass;
    private static final ArrayList<View> statusBarClocks = new ArrayList<>();
    private static long lastCheck;
    private static boolean hide;

    private static void log(String s) {
        android.util.Log.i("ClockWidgetHk", s);
        try {
            XposedBridge.log("[ClockWidget] " + s);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }
        log("注入 SystemUI，sdk=" + android.os.Build.VERSION.SDK_INT);
        try {
            clockClass = XposedHelpers.findClass(MIUI_CLOCK, lpparam.classLoader);
        } catch (Throwable t) {
            log("找不到 MiuiClock: " + t);
        }
        hookAttach(lpparam.classLoader, MIUI_CLOCK);
        hookAttach(lpparam.classLoader, POLICY_CLOCK);
        hookVisibility(lpparam.classLoader);
        startPoller();
    }

    private static void hookAttach(ClassLoader cl, final String className) {
        hook(cl, className, "onAttachedToWindow", new Class<?>[]{}, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) {
                    return;
                }
                View v = (View) param.thisObject;
                boolean target = isStatusBarTime(v);
                log("attach " + v.getClass().getSimpleName() + " id=" + idName(v)
                        + " 状态栏时间=" + target);
                if (target) {
                    remember(v);
                    apply(v, true);
                }
            }
        });
    }

    private static void hookVisibility(ClassLoader cl) {
        hook(cl, "android.view.View", "setVisibility", new Class<?>[]{int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (clockClass == null || !(param.thisObject instanceof View)) {
                            return;
                        }
                        View v = (View) param.thisObject;
                        if (!clockClass.isInstance(v) || !isStatusBarTime(v)) {
                            return;
                        }
                        int want = (Integer) param.args[0];
                        if (want != View.GONE && shouldHide(v, false)) {
                            param.args[0] = View.GONE;
                        }
                    }
                });
    }

    /** 桌面翻页后要尽快生效，光靠时钟自身的事件不够，定时纠一次。 */
    private static void startPoller() {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (statusBarClocks) {
                        for (int i = statusBarClocks.size() - 1; i >= 0; i--) {
                            View v = statusBarClocks.get(i);
                            if (v == null || v.getWindowToken() == null) {
                                statusBarClocks.remove(i);
                                continue;
                            }
                            apply(v, false);
                        }
                    }
                } catch (Throwable t) {
                    log("轮询异常: " + t);
                }
                handler.postDelayed(this, 800);
            }
        }, 800);
    }

    private static void remember(View v) {
        synchronized (statusBarClocks) {
            for (View o : statusBarClocks) {
                if (o == v) {
                    return;
                }
            }
            statusBarClocks.add(v);
        }
    }

    private static boolean isStatusBarTime(View v) {
        boolean timeId = false;
        String id = idName(v);
        for (String s : TIME_IDS) {
            if (s.equals(id)) {
                timeId = true;
                break;
            }
        }
        if (!timeId) {
            return false;
        }
        try {
            ViewParent p = v.getParent();
            for (int i = 0; i < 6 && p instanceof View; i++) {
                String pid = idName((View) p);
                for (String s : STATUS_BAR_IDS) {
                    if (s.equals(pid)) {
                        return true;
                    }
                }
                for (String s : NOT_STATUS_BAR_IDS) {
                    if (s.equals(pid)) {
                        return false;
                    }
                }
                p = p.getParent();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void apply(View view, boolean force) {
        try {
            if (view == null) {
                return;
            }
            boolean shouldHide = shouldHide(view, force);
            int before = view.getVisibility();
            view.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
            if (before != view.getVisibility()) {
                log("set " + idName(view) + " " + before + " -> " + view.getVisibility()
                        + " hide=" + hide);
            }
        } catch (Throwable t) {
            log("apply 失败: " + t);
        }
    }

    /** 读本应用写下的「桌面当前页有组件」标记（组件启用状态）。 */
    private static boolean shouldHide(View view, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastCheck < 500L) {
            return hide;
        }
        lastCheck = now;
        try {
            Context ctx = view.getContext();
            int state = ctx.getPackageManager().getComponentEnabledSetting(
                    new ComponentName(PKG, FLAG));
            hide = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Throwable t) {
            hide = false;
        }
        return hide;
    }

    private static String idName(View v) {
        try {
            if (v.getId() == View.NO_ID) return "NO_ID";
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return String.valueOf(v.getId());
        }
    }

    /** 反射调 findAndHookMethod：不依赖返回类型，兼容不同 LSPosed 版本。 */
    private static void hook(ClassLoader cl, String className, String methodName,
                             Class<?>[] paramTypes, XC_MethodHook cb) {
        try {
            Method find = findAndHookMethodOf(XposedHelpers.class);
            if (find == null) {
                log("运行时没有 findAndHookMethod，LSPosed 版本不兼容");
                return;
            }
            Object[] args = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, args, 0, paramTypes.length);
            args[paramTypes.length] = cb;
            find.invoke(null, className, cl, methodName, args);
            log("hooked " + className + "#" + methodName);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            log("hook 失败 " + className + "#" + methodName + " -> " + cause);
        }
    }

    private static Method findAndHookMethodOf(Class<?> helpers) {
        for (Method m : helpers.getDeclaredMethods()) {
            if (!"findAndHookMethod".equals(m.getName())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 4 && p[0] == String.class && p[1] == ClassLoader.class
                    && p[2] == String.class && p[3] == Object[].class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
