package com.zuakeo.clockwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * 换壁纸后的配色刷新。
 *
 * 这里刻意**不使用任何闹钟**。原因：setAlarmClock 会在状态栏/锁屏挂一个闹钟图标，
 * 对时钟组件是明显副作用；setExact 需要 SCHEDULE_EXACT_ALARM 权限；
 * setInexactRepeating 又会在系统里多一条常驻闹钟。
 *
 * 而周期刷新已经由 appwidget-provider 的 updatePeriodMillis 完成——那是
 * 由系统的 AppWidgetService 驱动，App 进程不必存活，也不需要任何权限。
 *
 * 因此这里只保留一件事：
 *   「运行时注册 WALLPAPER_CHANGED 接收器」——仅在 App 进程恰好存活的那段时间
 *   （刚添加组件、刚被系统唤醒刷新完）提供即时变色，属于白捡的加速，不承担正确性。
 *
 * 正确性由三处兜住：
 *   1. updatePeriodMillis = 30 分钟（系统驱动，App 不在也能触发 onUpdate）；
 *   2. 清单里静态注册的 WallpaperChangedReceiver（部分 ROM 会带
 *      FLAG_RECEIVER_INCLUDE_BACKGROUND 投递，能收到就是即时）；
 *   3. 用户点击组件 -> 立刻重新取色（手动兜底，零成本）。
 */
final class WallpaperWatch {

    private WallpaperWatch() {
    }

    /** 进程存活期间临时加一个即时监听；进程一死它自动消失，不留痕迹。 */
    static void start(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    ClockWidgetProvider.refreshAllAsync(ctx.getApplicationContext());
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Throwable ignored) {
            // 注册失败不影响组件正常工作，只是少了这条加速路径。
        }
    }
}
