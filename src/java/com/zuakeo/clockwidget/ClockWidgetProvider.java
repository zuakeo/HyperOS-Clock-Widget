package com.zuakeo.clockwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * 桌面时钟小组件。
 *
 * 架构（对应三条硬要求：随壁纸变色 / 留存后台但不出现在后台列表 / 占用极小）：
 *
 *  1) 时与分、日期都用 TextClock。
 *     TextClock 标了 @RemoteView，setFormat24Hour / setFormat12Hour 是
 *     @RemotableViewMethod，可被 RemoteViews 按方法名反射调用；
 *     它由**桌面（launcher）进程**持有并自己每分钟走字，
 *     所以组件运行期间本 App 进程完全不需要存在。
 *     没有前台服务、没有常驻通知、不进最近任务、不进后台进程列表。
 *
 *  2) 配色随壁纸明暗：只在本 App 恰好被唤醒时算一次并下发 RemoteViews。
 *     保证最终一致的刷新来自 appwidget-provider 的 updatePeriodMillis（30 分钟），
 *     由系统的 AppWidgetService 驱动，与本 App 是否存活无关。
 *
 *  3) 点击行为：
 *     - 点日期行 -> 日历（优先打开"今天"，降级为打开日历 App）
 *     - 点时间行 -> 时钟 App
 *     - 点空白处 -> 立刻重新取色（零成本的手动兜底）
 *
 *  4) 不需要 root，不需要任何权限。
 */
public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ClockWidget";

    /** 组件尺寸基准（dp）：对应 4 列 x 2 行。 */
    private static final int BASE_WIDTH_DP = 280;
    private static final int BASE_HEIGHT_DP = 120;

    /** 字号基准（sp）。 */
    private static final float BASE_TIME_SP = 56f;
    private static final float BASE_DATE_SP = 15f;

    /** 本 App 内部用的刷新 action（点击空白处时触发）。 */
    static final String ACTION_REFRESH = "com.zuakeo.clockwidget.action.REFRESH";

    private static final int RC_REFRESH = 0x0C11;
    private static final int RC_CLOCK = 0x0C20;
    private static final int RC_CALENDAR = 0x0C21;

    /** 各 ROM 常见的时钟 App 包名，按优先级尝试。 */
    private static final String[] CLOCK_PACKAGES = {
            "com.android.deskclock",        // AOSP / MIUI / HyperOS
            "com.miui.clock",
            "com.google.android.deskclock",
            "com.android.BBKClock",
            "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock",
            "com.oplus.deskclock",
    };

    /** 各 ROM 常见的日历 App 包名，按优先级尝试。 */
    private static final String[] CALENDAR_PACKAGES = {
            "com.android.calendar",         // AOSP / MIUI / HyperOS
            "com.miui.calendar",
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.oneplus.calendar",
            "com.oplus.calendar",
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            render(context, manager, id);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                         int appWidgetId, Bundle newOptions) {
        // 用户拖动缩放组件：重算字号与配色。
        render(context, manager, appWidgetId);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        // 进程刚好活着，顺手挂一个即时监听；进程一死它自动消失。
        WallpaperWatch.start(context.getApplicationContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            Log.i(TAG, "点击空白处 -> 重新取色");
            refreshAllAsync(context.getApplicationContext());
        }
    }

    /** 异步刷新全部组件实例（供广播接收器调用，避免占住主线程）。 */
    static void refreshAllAsync(final Context context) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AppWidgetManager manager = AppWidgetManager.getInstance(context);
                    int[] ids = manager.getAppWidgetIds(
                            new ComponentName(context, ClockWidgetProvider.class));
                    if (ids == null || ids.length == 0) {
                        return;
                    }
                    WallpaperTone.Result tone = WallpaperTone.resolve(context);
                    for (int id : ids) {
                        push(context, manager, id, tone);
                    }
                } catch (Throwable t) {
                    Log.i(TAG, "刷新失败: " + t);
                }
            }
        }, "clockwidget-refresh");
        worker.start();
    }

    /** 单个组件的渲染入口：取色在后台线程完成，避免主线程 ANR。 */
    static void render(final Context context, final AppWidgetManager manager, final int widgetId) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                WallpaperTone.Result tone;
                try {
                    tone = WallpaperTone.resolve(context);
                } catch (Throwable t) {
                    tone = new WallpaperTone.Result(true, 0xff000000);
                }
                push(context, manager, widgetId, tone);
            }
        }, "clockwidget-render");
        worker.start();
    }

    /** 组装并下发 RemoteViews。 */
    private static void push(Context context, AppWidgetManager manager, int widgetId,
                             WallpaperTone.Result tone) {
        try {
            Bundle options = manager.getAppWidgetOptions(widgetId);
            int minWidth = options == null ? BASE_WIDTH_DP
                    : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, BASE_WIDTH_DP);
            int minHeight = options == null ? BASE_HEIGHT_DP
                    : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, BASE_HEIGHT_DP);
            RemoteViews views = build(context, tone.darkBackground, minWidth, minHeight);
            manager.updateAppWidget(widgetId, views);
            Log.i(TAG, "已下发 widgetId=" + widgetId + " 深色底=" + tone.darkBackground
                    + " 尺寸=" + minWidth + "x" + minHeight);
        } catch (Throwable t) {
            Log.i(TAG, "下发失败 widgetId=" + widgetId + ": " + t);
        }
    }

    private static RemoteViews build(Context context, boolean darkBackground,
                                     int minWidth, int minHeight) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                darkBackground ? R.layout.widget_clock_dark : R.layout.widget_clock_light);

        // 时/分：只到分钟，不要秒。桌面进程自己每分钟走字。
        views.setCharSequence(R.id.widget_time, "setFormat24Hour", "HH:mm");
        views.setCharSequence(R.id.widget_time, "setFormat12Hour", "h:mm");

        // 日期行：同样是 TextClock，走 android.text.format.DateFormat，
        // 支持 M / d / E 等字符，跨天由桌面进程自己更新。
        String dateFormat = context.getString(R.string.date_format);
        views.setCharSequence(R.id.widget_date, "setFormat24Hour", dateFormat);
        views.setCharSequence(R.id.widget_date, "setFormat12Hour", dateFormat);

        // 字号随组件实际尺寸缩放 —— 这就是"可以调节大小"的实现。
        float scale = Math.max(0.5f, Math.min(2.0f,
                Math.min(minWidth / (float) BASE_WIDTH_DP, minHeight / (float) BASE_HEIGHT_DP)));
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP,
                Math.round(BASE_TIME_SP * scale));
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP,
                Math.max(11f, Math.round(BASE_DATE_SP * scale)));

        // 点空白处 = 立刻重新取色（零成本的手动兜底）。
        views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent(context));

        // 点时间行 -> 时钟 App。
        PendingIntent clock = activityPendingIntent(context, clockIntent(context), RC_CLOCK);
        if (clock != null) {
            views.setOnClickPendingIntent(R.id.widget_time_touch, clock);
        }

        // 点日期行 -> 日历（优先"今天"）。
        PendingIntent calendar = activityPendingIntent(context, calendarIntent(context), RC_CALENDAR);
        if (calendar != null) {
            views.setOnClickPendingIntent(R.id.widget_date_touch, calendar);
        }

        return views;
    }

    /** 点空白处走广播（不是 Activity），单独包一层。 */
    private static PendingIntent refreshPendingIntent(Context context) {
        Intent refresh = new Intent(context, ClockWidgetProvider.class);
        refresh.setAction(ACTION_REFRESH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, RC_REFRESH, refresh, flags);
    }

    private static PendingIntent activityPendingIntent(Context context, Intent intent,
                                                       int requestCode) {
        if (intent == null) {
            return null;
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    /**
     * 时钟 App 的启动 Intent。
     * 先走各 ROM 的启动入口；再退回标准 SHOW_ALARMS（且必须落在时钟类应用上）。
     * 都拿不到就返回 null —— 宁可不设点击，也不要弹系统选择器。
     */
    private static Intent clockIntent(Context context) {
        ComponentName launcher = resolveLauncher(context, CLOCK_PACKAGES);
        if (launcher != null) {
            Log.i(TAG, "时钟入口=" + launcher.flattenToShortString());
            return explicit(launcher);
        }
        Intent showAlarms = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        ComponentName alarm = resolve(context, showAlarms);
        if (alarm != null && looksLike(alarm.getPackageName(), "clock", CLOCK_PACKAGES)) {
            Log.i(TAG, "时钟入口(SHOW_ALARMS)=" + alarm.flattenToShortString());
            return showAlarms.setComponent(alarm);
        }
        Log.i(TAG, "没找到可用的时钟 App");
        return null;
    }

    /**
     * 日历 App 的启动 Intent。
     *
     * 顺序：各 ROM 的日历入口（最稳，不会弹选择器）-> "今天"视图 -> APP_CALENDAR。
     * "今天"视图只在该 URI 唯一落在日历类应用上时才用，否则会弹出
     * "网易云音乐/网盘/日历…" 这种多应用选择器（踩过）。
     */
    private static Intent calendarIntent(Context context) {
        ComponentName launcher = resolveLauncher(context, CALENDAR_PACKAGES);
        if (launcher != null) {
            Log.i(TAG, "日历入口=" + launcher.flattenToShortString());
            return explicit(launcher);
        }

        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, System.currentTimeMillis());
        Intent today = new Intent(Intent.ACTION_VIEW).setData(builder.build());
        ComponentName timeView = resolve(context, today);
        if (timeView != null && looksLike(timeView.getPackageName(), "calendar", CALENDAR_PACKAGES)) {
            Log.i(TAG, "日历入口(今天)=" + timeView.flattenToShortString());
            return today.setComponent(timeView);
        }

        Intent appCalendar = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR);
        ComponentName generic = resolve(context, appCalendar);
        if (generic != null && looksLike(generic.getPackageName(), "calendar", CALENDAR_PACKAGES)) {
            Log.i(TAG, "日历入口(APP_CALENDAR)=" + generic.flattenToShortString());
            return appCalendar.setComponent(generic);
        }
        Log.i(TAG, "没找到可用的日历 App");
        return null;
    }

    private static Intent explicit(ComponentName component) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component);
    }

    /** 包名是否像目标应用（含关键字或在候选清单里）。 */
    private static boolean looksLike(String pkg, String keyword, String[] known) {
        if (pkg == null) {
            return false;
        }
        if (pkg.toLowerCase(java.util.Locale.ROOT).contains(keyword)) {
            return true;
        }
        for (String candidate : known) {
            if (candidate.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** 在候选包名里找第一个能启动的入口。 */
    private static ComponentName resolveLauncher(Context context, String[] packages) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : packages) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null && launch.getComponent() != null) {
                    return launch.getComponent();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 解析隐式 Intent 的目标组件，跳过系统选择器（package=android）。 */
    private static ComponentName resolve(Context context, Intent intent) {
        try {
            ResolveInfo info = context.getPackageManager()
                    .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null && info.activityInfo != null
                    && !"android".equals(info.activityInfo.packageName)) {
                return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
