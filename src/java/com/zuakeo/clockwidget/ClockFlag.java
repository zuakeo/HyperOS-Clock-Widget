package com.zuakeo.clockwidget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * 「桌面上还有没有本应用的组件」这个信号，用一个空组件的启用状态表示。
 *
 * 为什么绕这一圈：模块跑在 SystemUI 里，Android 17 上
 * `AppWidgetManager.getAppWidgetIds(别人家的 Provider)` 直接抛 SecurityException
 * （uid 1000 也没有 BIND_APPWIDGET），拿不到组件列表；而
 * `PackageManager.getComponentEnabledSetting()` 跨包读**不需要任何权限**。
 *
 * 本组件在清单里 android:enabled="false"，所以默认就是「没有组件」。
 * 这个类同时是那个被开关的 receiver 本身（没有 intent-filter，只当标记用）。
 */
public class ClockFlag extends BroadcastReceiver {

    public static final String COMPONENT = "com.zuakeo.clockwidget.ClockFlag";

    @Override
    public void onReceive(Context context, android.content.Intent intent) {
        sync(context);
    }

    /** 按当前桌面组件数量刷新标记；组件增删后由两个 Provider 调用。 */
    public static void sync(Context context) {
        if (context == null) {
            return;
        }
        try {
            Context app = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            boolean has = count(app, manager, ClockWidgetProvider.class) > 0
                    || count(app, manager, ClockWeatherWidgetProvider.class) > 0;
            app.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(app, COMPONENT),
                    has ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable ignored) {
            // 标记失败不影响组件本身。
        }
    }

    private static int count(Context context, AppWidgetManager manager, Class<?> provider) {
        try {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
            return ids == null ? 0 : ids.length;
        } catch (Throwable t) {
            return 0;
        }
    }
}
