package com.zuakeo.clockwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 本 app 自带的 LSPosed 模块：桌面存在时钟组件时，把状态栏的时间藏掉。
 *
 * 为什么要 hook：HyperOS 4 的状态栏时间是 MiuiClock（自绘 TextView，
 * 时间和日期可能在同一段文本里），Android 标准的 icon_blacklist /
 * status_bar_show_clock 在 MIUI 上被忽略，root 改设置也没用。
 *
 * 生效条件：
 *   1. 在 LSPosed 里启用本应用，作用域勾选「系统界面 / SystemUI」；
 *   2. 重启系统界面或重启设备。
 * 没有组件时完全不做任何事（不 hook、不改可见性）。
 */
public class ClockHideModule implements IXposedHookLoadPackage {

    private static final String PKG = "com.zuakeo.clockwidget";
    private static final String WEATHER_PROVIDER = PKG + ".ClockWeatherWidgetProvider";
    private static final String CLOCK_PROVIDER = PKG + ".ClockWidgetProvider";

    private static long lastCheck;
    private static boolean hide;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }
        hook(lpparam, "com.android.systemui.statusbar.views.MiuiClock");
        hook(lpparam, "com.android.systemui.statusbar.policy.Clock");
    }

    private static void hook(XC_LoadPackage.LoadPackageParam lp, final String className) {
        try {
            XposedHelpers.findAndHookMethod(className, lp.classLoader, "onAttachedToWindow",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            apply((View) param.thisObject, true);
                        }
                    });
            try {
                XposedHelpers.findAndHookMethod(className, lp.classLoader, "updateClock",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                apply((View) param.thisObject, false);
                            }
                        });
            } catch (Throwable ignored) {
                // 这个方法在部分版本不存在，onAttachedToWindow 仍然有效。
            }
            XposedBridge.log("[ClockWidget] hooked " + className);
        } catch (Throwable t) {
            XposedBridge.log("[ClockWidget] hook " + className + " 失败: " + t);
        }
    }

    private static void apply(View view, boolean force) {
        try {
            if (view == null) {
                return;
            }
            boolean shouldHide = shouldHide(view, force);
            view.setVisibility(shouldHide ? View.GONE : View.VISIBLE);
        } catch (Throwable t) {
            XposedBridge.log("[ClockWidget] apply 失败: " + t);
        }
    }

    private static boolean shouldHide(View view, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastCheck < 10000L) {
            return hide;
        }
        lastCheck = now;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(view.getContext());
            hide = hasWidget(manager, WEATHER_PROVIDER) || hasWidget(manager, CLOCK_PROVIDER);
        } catch (Throwable t) {
            hide = false;
        }
        return hide;
    }

    private static boolean hasWidget(AppWidgetManager manager, String provider) {
        try {
            int[] ids = manager.getAppWidgetIds(new ComponentName(PKG, provider));
            return ids != null && ids.length > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
