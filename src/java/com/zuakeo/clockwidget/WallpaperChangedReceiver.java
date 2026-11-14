package com.zuakeo.clockwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 换壁纸广播。
 *
 * 事实：ACTION_WALLPAPER_CHANGED 不在 Android 8 起的「隐式广播豁免名单」内，
 * 因此当本 App 处于后台时，**这个静态注册的接收器可能收不到**。
 *
 * 那为什么还要注册它？
 *   - 部分 ROM（含部分 MIUI/HyperOS 版本）的 WallpaperManagerService 在发送时
 *     带了 FLAG_RECEIVER_INCLUDE_BACKGROUND，此时后台应用也能收到，能做到即时变色；
 *   - 收不到时完全没有代价：只是一条永远不触发的清单项，不占内存、不起进程；
 *   - 真正保证最终一致的，是 updatePeriodMillis 的 30 分钟周期刷新 + 点击手动刷新。
 */
public class WallpaperChangedReceiver extends BroadcastReceiver {

    private static final String TAG = "ClockWidget";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_WALLPAPER_CHANGED.equals(intent.getAction())) {
            return;
        }
        Log.i(TAG, "收到换壁纸广播 -> 重新取色");
        ClockWidgetProvider.refreshAllAsync(context.getApplicationContext());
    }
}
