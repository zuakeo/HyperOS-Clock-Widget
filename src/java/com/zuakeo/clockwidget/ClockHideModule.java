package com.zuakeo.clockwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 本 app 自带的 LSPosed 模块：桌面存在时钟组件时，把状态栏的时间藏掉（只留日期）。
 *
 * 为什么要 hook：HyperOS 4 忽略 Android 的 icon_blacklist / status_bar_show_clock，
 * root 改设置、重启系统界面都无效，状态栏时间是 MIUI 自己画的控件。
 *
 * 生效条件：在 LSPosed 里启用本应用，作用域勾选「系统界面 / SystemUI」，然后重启系统界面。
 *
 * ── 踩过的坑（都实测过，别再退回）────────────────────────
 * 1. hook 必须反射调：ART 解析 invoke-static 时**返回类型也算签名**，
 *    findAndHookMethod 官方返回 XC_MethodHook.Unhook，v1.6 的编译桩写成 void，
 *    每个调用都抛 NoSuchMethodError（被 catch 吞了，日志里只见「hook 失败」）。
 * 2. 不能查 AppWidgetManager：SystemUI 里 getAppWidgetIds(别人家的 Provider) 在 Android 17
 *    直接 SecurityException（uid 1000 也没 BIND_APPWIDGET）。改读本应用标记组件的启用状态。
 * 3. MiuiClock 全家族共用：锁屏时钟、通知栏大时钟、下拉日期都是它/它的子类，
 *    只能按「父链里有状态栏容器 + id 是 clock/pad_clock」认。
 * 4. 设一次 GONE 不够：MIUI 每次刷新会把时钟改回 VISIBLE，必须在 View#setVisibility
 *    上拦截参数（before hook 改 args，不改写不递归）。
 */
public class ClockHideModule implements IXposedHookLoadPackage {

    private static final String PKG = "com.zuakeo.clockwidget";
    /** 与 ClockFlag.COMPONENT 保持一致。 */
    private static final String FLAG = PKG + ".ClockFlag";

    private static final String MIUI_CLOCK = "com.android.systemui.statusbar.views.MiuiClock";
    private static final String POLICY_CLOCK = "com.android.systemui.statusbar.policy.Clock";
    /** 父链里出现这些 id 说明是状态栏；出现锁屏/通知栏的 id 就直接判定不是。 */
    private static final String[] STATUS_BAR_IDS = {
            "status_bar_contents", "phone_status_bar_left_container",
    };
    private static final String[] NOT_STATUS_BAR_IDS = {
            "keyguard_status_bar_content", "notification_panel", "shade_header_container",
    };
    /**
     * 状态栏里显示**时间**的视图 id。
     * 实测（Redmi K Pad / HyperOS 4）：左上角两个控件是
     *   clock     = StatusBarClock，text 是 "00:20"（时间）
     *   pad_clock = MiuiClock，text 是 "11月17日 周二"（日期！）
     * 所以只能藏 clock；pad_clock 是日期，藏了日期就没了。
     */
    private static final String[] TIME_IDS = {"clock"};

    private static Class<?> clockClass;
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
    }

    /** 时钟控件挂到窗口时认一次：是不是状态栏里的那个，是就先藏起来。 */
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
                        + " 状态栏时间=" + target + " vis=" + v.getVisibility()
                        + " text=[" + textOf(v) + "]");
                if (target) {
                    apply(v, true);
                }
            }
        });
    }

    /** MIUI 会把时钟改回 VISIBLE：在它设可见性时把参数改掉。 */
    private static void hookVisibility(ClassLoader cl) {
        hook(cl, "android.view.View", "setVisibility", new Class<?>[]{int.class},
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!HIDE_ENABLED || clockClass == null
                                || !(param.thisObject instanceof View)) {
                            return;
                        }
                        View v = (View) param.thisObject;
                        if (!clockClass.isInstance(v)) {
                            return;                     // 绝大多数 View 在这里就返回了
                        }
                        if (!isStatusBarTime(v)) {
                            return;
                        }
                        int want = (Integer) param.args[0];
                        if (want != View.GONE && shouldHide(v, false)) {
                            param.args[0] = View.GONE;
                        }
                    }
                });
    }

    /** 状态栏里显示时间的那个视图（clock / pad_clock），锁屏和通知栏的一概不算。 */
    private static boolean isStatusBarTime(View v) {
        String id = idName(v);
        boolean timeId = false;
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

    private static String textOf(View v) {
        try {
            if (v instanceof android.widget.TextView) {
                CharSequence c = ((android.widget.TextView) v).getText();
                return c == null ? "null" : c.toString().replace("\n", "\\n");
            }
        } catch (Throwable ignored) {
        }
        return "-";
    }

    /** 看父容器里还有哪些兄弟控件（判断时间和日期是不是两个 View）。 */
    private static void dumpSiblings(View v) {
        try {
            android.view.ViewParent p = v.getParent();
            if (!(p instanceof android.view.ViewGroup)) {
                return;
            }
            android.view.ViewGroup g = (android.view.ViewGroup) p;
            StringBuilder sb = new StringBuilder("  兄弟: ");
            for (int i = 0; i < g.getChildCount() && i < 8; i++) {
                View c = g.getChildAt(i);
                sb.append('[').append(c.getClass().getSimpleName())
                        .append(" id=").append(idName(c))
                        .append(" vis=").append(c.getVisibility())
                        .append(" text=").append(textOf(c))
                        .append(" w=").append(c.getWidth())
                        .append(']');
            }
            log(sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static String idName(View v) {
        try {
            if (v.getId() == View.NO_ID) return "NO_ID";
            return v.getResources().getResourceEntryName(v.getId());
        } catch (Throwable t) {
            return String.valueOf(v.getId());
        }
    }

    private static final boolean HIDE_ENABLED = true;

    private static void apply(View view, boolean force) {
        try {
            if (view == null) {
                return;
            }
            boolean shouldHide = shouldHide(view, force);
            if (!HIDE_ENABLED) {
                return;
            }
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

    /**
     * 桌面还有组件吗？读本应用那个标记组件的启用状态。
     * 不用 AppWidgetManager：SystemUI 里查别人的 Provider 会 SecurityException。
     */
    private static boolean shouldHide(View view, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastCheck < 3000L) {
            return hide;
        }
        lastCheck = now;
        try {
            Context ctx = view.getContext();
            int state = ctx.getPackageManager().getComponentEnabledSetting(
                    new ComponentName(PKG, FLAG));
            hide = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            if (force) {
                log("标记组件 state=" + state + " hide=" + hide);
            }
        } catch (Throwable t) {
            hide = false;
            log("读标记失败: " + t);
        }
        return hide;
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
