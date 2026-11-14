package com.zuakeo.clockwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 监听系统广播：改时间 / 改时区 / 改语言 -> 重新取色并刷新组件。
 *
 * 这三个 action 都在 Android 8 起的"隐式广播豁免名单"内，
 * manifest 静态注册有效，后台也能收到。
 */
public class SystemEventReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return;
        }
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            ClockWidgetProvider.refreshAllAsync(context.getApplicationContext());
        }
    }
}
