package com.zuakeo.clockwidget;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 「桌面当前页有没有本应用组件」这个信号，用一个空组件的启用状态表示。
 *
 * 为什么绕这一圈：模块跑在 SystemUI 里，Android 17 上
 * `AppWidgetManager.getAppWidgetIds(别人家的 Provider)` 直接抛 SecurityException
 * （uid 1000 也没有 BIND_APPWIDGET）；而 `PackageManager.getComponentEnabledSetting()`
 * 跨包读**不需要任何权限**。所以：本应用写、模块读。
 *
 * 写的人是 {@link PageWatchService}（无障碍服务，看桌面当前页的节点树）。
 * 本组件在清单里 android:enabled="false"，默认就是「没有」。
 */
public class ClockFlag extends BroadcastReceiver {

    public static final String PKG = "com.zuakeo.clockwidget";
    public static final String COMPONENT = PKG + ".ClockFlag";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 只是当标记用的空组件，没有任何逻辑。
    }

    /** 桌面当前页是否有组件。 */
    public static void setOnPage(Context context, boolean onPage) {
        if (context == null) {
            return;
        }
        try {
            Context app = context.getApplicationContext();
            PackageManager pm = app.getPackageManager();
            ComponentName cn = new ComponentName(app, COMPONENT);
            int want = onPage ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (pm.getComponentEnabledSetting(cn) != want) {
                pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable ignored) {
            // 标记失败不影响组件本身。
        }
    }
}
