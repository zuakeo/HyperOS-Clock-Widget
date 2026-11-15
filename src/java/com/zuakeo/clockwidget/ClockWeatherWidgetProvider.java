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
import android.view.View;
import android.widget.RemoteViews;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 第二个桌面组件：日期 + 农历 + 天气 + 大号时间。
 *
 * 与 {@link ClockWidgetProvider} 完全独立：各自的布局、各自的 appwidget-provider、
 * 各自的广播接收器。两个组件可以同时摆在桌面上，互不影响。
 *
 * 数据来源：
 *  - 时间/日期：TextClock，由桌面进程走字，本 App 不常驻；
 *  - 农历：LunarDate 本地换算；
 *  - 天气：Open-Meteo 公开接口（免 Key），每 30 分钟随组件刷新拉一次，
 *    结果缓存在 SharedPreferences，断网时继续显示上一次的温度。
 *
 * 显示项、字号、温度单位、城市都由 {@link WidgetSettings} 控制；
 * 字重是布局级属性，按 4 档预生成布局（tools/gen_widget_layouts.py）。
 *
 * 点击：
 *  - 日期行 -> 日历
 *  - 时间行 -> 时钟
 *  - 天气区 / 城市名 -> 选城市
 *  - 空白处 -> 立刻刷新天气
 */
public class ClockWeatherWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ClockWeatherWidget";

    /** 组件尺寸基准（dp）：4 列 x 2 行。 */
    private static final int BASE_WIDTH_DP = 280;
    private static final int BASE_HEIGHT_DP = 120;

    /** 缓存超过这个时长就重新拉天气。 */
    private static final long WEATHER_MAX_AGE_MS = 25L * 60L * 1000L;

    /** 联网刷新天气。 */
    static final String ACTION_REFRESH = "com.zuakeo.clockwidget.action.WEATHER_REFRESH";
    /** 只按新设置重排，不联网。 */
    static final String ACTION_RERENDER = "com.zuakeo.clockwidget.action.RERENDER";

    private static final int RC_REFRESH = 0x0D11;
    private static final int RC_CLOCK = 0x0D20;
    private static final int RC_CALENDAR = 0x0D21;
    private static final int RC_CITY = 0x0D22;

    /** 防止系统连发广播时并发拉天气。 */
    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    private static final String[] CLOCK_PACKAGES = {
            "com.android.deskclock", "com.miui.clock", "com.google.android.deskclock",
            "com.android.BBKClock", "com.sec.android.app.clockpackage",
            "com.oneplus.deskclock", "com.oplus.deskclock",
    };

    private static final String[] CALENDAR_PACKAGES = {
            "com.android.calendar", "com.miui.calendar", "com.google.android.calendar",
            "com.samsung.android.calendar", "com.oneplus.calendar", "com.oplus.calendar",
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_RERENDER.equals(action)) {
            final PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        rerenderAll(app);
                    } catch (Throwable t) {
                        Log.i(TAG, "重排失败: " + t);
                    } finally {
                        pending.finish();
                    }
                }
            }, "clockweather-rerender").start();
            return;
        }

        // APPWIDGET_UPDATE 与手动刷新都要联网，必须在 goAsync 的有效期内干完，
        // 所以这里不走 super（super 会同步调 onUpdate，进程随时可能被回收）。
        if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || ACTION_REFRESH.equals(action)) {
            final PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            final boolean force = ACTION_REFRESH.equals(action);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        refreshAll(app, force);
                    } catch (Throwable t) {
                        Log.i(TAG, "刷新失败: " + t);
                    } finally {
                        pending.finish();
                    }
                }
            }, "clockweather-update").start();
            return;
        }

        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        // 正常路径被 onReceive 拦下了；这里只做兜底：按设置推缓存，不联网。
        rerenderAll(context.getApplicationContext());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, Bundle newOptions) {
        // 拖动缩放：按新尺寸重排字号，天气用缓存。
        Context app = context.getApplicationContext();
        push(app, manager, appWidgetId, tone(app), WeatherStore.load(app),
                WidgetSettings.load(app));
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        WallpaperWatch.start(context.getApplicationContext());
    }

    /** 只按当前设置与缓存重排，不联网。 */
    static void rerenderAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, ClockWeatherWidgetProvider.class));
        if (ids == null || ids.length == 0) {
            return;
        }
        WallpaperTone.Result tone = tone(context);
        WeatherStore.Snapshot snapshot = WeatherStore.load(context);
        WidgetSettings settings = WidgetSettings.load(context);
        for (int id : ids) {
            push(context, manager, id, tone, snapshot, settings);
        }
    }

    /** 推送缓存 + 必要时联网，最后再推一次。 */
    static void refreshAll(Context context, boolean force) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName self = new ComponentName(context, ClockWeatherWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(self);
        if (ids == null || ids.length == 0) {
            return;
        }

        WallpaperTone.Result tone = tone(context);
        WeatherStore.Snapshot cached = WeatherStore.load(context);
        WidgetSettings settings = WidgetSettings.load(context);

        // 先把缓存推到桌面，避免刷新期间组件空白。
        for (int id : ids) {
            push(context, manager, id, tone, cached, settings);
        }

        long now = System.currentTimeMillis();
        if (!force && !cached.stale(now, WEATHER_MAX_AGE_MS)) {
            return;
        }
        if (!FETCHING.compareAndSet(false, true)) {
            return;
        }
        try {
            WeatherClient.Now fresh = WeatherClient.current(cached.lat, cached.lon);
            WeatherStore.saveNow(context, fresh.tempC, fresh.code, fresh.day, now);
            WeatherStore.Snapshot updated = WeatherStore.load(context);
            WeatherClient.log("天气 " + updated.city + " " + fresh.tempC + "° code=" + fresh.code
                    + " 白天=" + fresh.day);
            for (int id : ids) {
                push(context, manager, id, tone, updated, settings);
            }
        } catch (Throwable t) {
            WeatherClient.log("天气拉取失败（继续用缓存）: " + t);
        } finally {
            FETCHING.set(false);
        }
    }

    private static WallpaperTone.Result tone(Context context) {
        try {
            return WallpaperTone.resolve(context);
        } catch (Throwable t) {
            return new WallpaperTone.Result(true, 0xff000000);
        }
    }

    private static void push(Context context, AppWidgetManager manager, int widgetId,
                             WallpaperTone.Result tone, WeatherStore.Snapshot weather,
                             WidgetSettings settings) {
        try {
            Bundle options = manager.getAppWidgetOptions(widgetId);
            int minWidth = options == null ? BASE_WIDTH_DP
                    : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, BASE_WIDTH_DP);
            int minHeight = options == null ? BASE_HEIGHT_DP
                    : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, BASE_HEIGHT_DP);
            manager.updateAppWidget(widgetId,
                    build(context, tone.darkBackground, minWidth, minHeight, weather, settings));
        } catch (Throwable t) {
            Log.i(TAG, "下发失败 widgetId=" + widgetId + ": " + t);
        }
    }

    private static RemoteViews build(Context context, boolean darkBackground,
                                     int minWidth, int minHeight,
                                     WeatherStore.Snapshot weather, WidgetSettings settings) {
        // 文字颜色：默认跟随壁纸明暗；壁纸明暗反差大时可在设置里强制白/深。
        boolean whiteText = isWhiteText(settings, darkBackground);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                layoutFor(whiteText, settings.weight));

        // 时间：秒是可选项；跨天与走字都在桌面进程里完成。
        String time24 = settings.showSeconds ? "HH:mm:ss" : "HH:mm";
        String time12 = settings.showSeconds ? "h:mm:ss" : "h:mm";
        views.setCharSequence(R.id.ww_time, "setFormat24Hour", time24);
        views.setCharSequence(R.id.ww_time, "setFormat12Hour", time12);

        // 日期：星期可关。
        String dateFormat = context.getString(settings.showWeekday
                ? R.string.date_format_compact : R.string.date_format_no_week);
        views.setCharSequence(R.id.ww_date, "setFormat24Hour", dateFormat);
        views.setCharSequence(R.id.ww_date, "setFormat12Hour", dateFormat);
        views.setViewVisibility(R.id.ww_date, settings.showDate ? View.VISIBLE : View.GONE);

        // 农历：本地换算，随每次组件刷新更新。
        String lunar = settings.showLunar ? LunarDate.format(System.currentTimeMillis()) : "";
        views.setTextViewText(R.id.ww_lunar, lunar);
        views.setViewVisibility(R.id.ww_lunar,
                lunar.length() > 0 ? View.VISIBLE : View.GONE);

        // 天气：有缓存就显示；图标按天气代码 + 昼夜切换，颜色跟文字一致。
        if (weather.hasTemp && settings.showIcon) {
            views.setImageViewResource(R.id.ww_icon, whiteText
                    ? WeatherClient.iconState(weather.code, weather.day)
                    : WeatherClient.iconStateDark(weather.code, weather.day));
            views.setViewVisibility(R.id.ww_icon, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.ww_icon, View.GONE);
        }

        if (settings.showTemp) {
            int shown = settings.fahrenheit
                    ? Math.round(weather.temp * 9f / 5f + 32f)
                    : weather.temp;
            views.setTextViewText(R.id.ww_temp, weather.hasTemp ? shown + "°" : "--°");
            views.setViewVisibility(R.id.ww_temp, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.ww_temp, View.GONE);
        }

        // 城市名（可选）
        String city = weather.city == null ? "" : weather.city;
        views.setTextViewText(R.id.ww_city, city);
        views.setViewVisibility(R.id.ww_city,
                settings.showCity && city.length() > 0 ? View.VISIBLE : View.GONE);

        // 整行都没内容时把行隐藏，避免留一条空白。
        boolean rowVisible = settings.showDate || settings.showLunar
                || settings.showIcon || settings.showTemp
                || (settings.showCity && city.length() > 0);
        views.setViewVisibility(R.id.ww_date_touch,
                rowVisible ? View.VISIBLE : View.GONE);

        // 字号：以设置里的 sp 为基准，再按组件实际尺寸缩放。
        float scale = Math.max(0.6f, Math.min(1.9f,
                Math.min(minWidth / (float) BASE_WIDTH_DP, minHeight / (float) BASE_HEIGHT_DP)));
        int timeSp = Math.max(24, Math.round(settings.timeSizeSp * scale));
        int dateSp = Math.max(9, Math.round(settings.dateSizeSp * scale));
        int lunarSp = Math.max(9, Math.round((settings.dateSizeSp - 1) * scale));
        int tempSp = Math.max(9, Math.round(settings.dateSizeSp * scale));
        int citySp = Math.max(8, Math.round((settings.dateSizeSp - 2) * scale));

        views.setTextViewTextSize(R.id.ww_time, TypedValue.COMPLEX_UNIT_SP, timeSp);
        views.setTextViewTextSize(R.id.ww_date, TypedValue.COMPLEX_UNIT_SP, dateSp);
        views.setTextViewTextSize(R.id.ww_lunar, TypedValue.COMPLEX_UNIT_SP, lunarSp);
        views.setTextViewTextSize(R.id.ww_temp, TypedValue.COMPLEX_UNIT_SP, tempSp);
        views.setTextViewTextSize(R.id.ww_city, TypedValue.COMPLEX_UNIT_SP, citySp);

        // 点击：空白 -> 刷新天气。
        views.setOnClickPendingIntent(R.id.ww_root, refreshPendingIntent(context));

        PendingIntent clock = activityPendingIntent(context, clockIntent(context), RC_CLOCK);
        if (clock != null) {
            views.setOnClickPendingIntent(R.id.ww_time_touch, clock);
        }

        PendingIntent calendar = activityPendingIntent(context, calendarIntent(context), RC_CALENDAR);
        if (calendar != null) {
            views.setOnClickPendingIntent(R.id.ww_date_touch, calendar);
        }

        // 天气区 / 城市名 -> 选城市。
        PendingIntent cityIntent = activityPendingIntent(context,
                new Intent(context, CityPickerActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP), RC_CITY);
        if (cityIntent != null) {
            views.setOnClickPendingIntent(R.id.ww_weather_touch, cityIntent);
            views.setOnClickPendingIntent(R.id.ww_city, cityIntent);
        }

        return views;
    }

    /** 文字用白还是深：设置里强制优先，否则跟随壁纸明暗。 */
    static boolean isWhiteText(WidgetSettings settings, boolean darkBackground) {
        if (WidgetSettings.C_WHITE.equals(settings.textColor)) {
            return true;
        }
        if (WidgetSettings.C_DARK.equals(settings.textColor)) {
            return false;
        }
        return darkBackground;
    }

    /** 按文字色与字重选布局（字重只能靠布局，运行时改不了 fontFamily）。 */
    private static int layoutFor(boolean whiteText, String weight) {
        if (whiteText) { // 白字
            switch (weight) {
                case WidgetSettings.W_LIGHT:
                    return R.layout.ww_white_light;
                case WidgetSettings.W_NORMAL:
                    return R.layout.ww_white_normal;
                case WidgetSettings.W_BOLD:
                    return R.layout.ww_white_bold;
                default:
                    return R.layout.ww_white_medium;
            }
        }
        switch (weight) { // 壁纸偏亮 -> 深字
            case WidgetSettings.W_LIGHT:
                return R.layout.ww_black_light;
            case WidgetSettings.W_NORMAL:
                return R.layout.ww_black_normal;
            case WidgetSettings.W_BOLD:
                return R.layout.ww_black_bold;
            default:
                return R.layout.ww_black_medium;
        }
    }

    private static PendingIntent refreshPendingIntent(Context context) {
        Intent refresh = new Intent(context, ClockWeatherWidgetProvider.class);
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

    // ---------------------------------------------------------------- 点击跳转

    private static Intent clockIntent(Context context) {
        ComponentName launcher = resolveLauncher(context, CLOCK_PACKAGES);
        if (launcher != null) {
            return explicit(launcher);
        }
        Intent showAlarms = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        ComponentName alarm = resolve(context, showAlarms);
        if (alarm != null && looksLike(alarm.getPackageName(), "clock", CLOCK_PACKAGES)) {
            return showAlarms.setComponent(alarm);
        }
        return null;
    }

    private static Intent calendarIntent(Context context) {
        ComponentName launcher = resolveLauncher(context, CALENDAR_PACKAGES);
        if (launcher != null) {
            return explicit(launcher);
        }

        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendPath("time");
        ContentUris.appendId(builder, System.currentTimeMillis());
        Intent today = new Intent(Intent.ACTION_VIEW).setData(builder.build());
        ComponentName timeView = resolve(context, today);
        if (timeView != null && looksLike(timeView.getPackageName(), "calendar", CALENDAR_PACKAGES)) {
            return today.setComponent(timeView);
        }

        Intent appCalendar = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR);
        ComponentName generic = resolve(context, appCalendar);
        if (generic != null && looksLike(generic.getPackageName(), "calendar", CALENDAR_PACKAGES)) {
            return appCalendar.setComponent(generic);
        }
        return null;
    }

    private static Intent explicit(ComponentName component) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component);
    }

    private static boolean looksLike(String pkg, String keyword, String[] known) {
        if (pkg == null) {
            return false;
        }
        if (pkg.toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        for (String candidate : known) {
            if (candidate.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

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

    /** 设置页改完显示项后调用：只重排，不联网。 */
    static void requestRerender(Context context) {
        Intent intent = new Intent(context, ClockWeatherWidgetProvider.class);
        intent.setAction(ACTION_RERENDER);
        context.sendBroadcast(intent);
    }

    /** 选完城市 / 点刷新后调用：联网拉一次。 */
    static void requestRefresh(Context context) {
        Intent intent = new Intent(context, ClockWeatherWidgetProvider.class);
        intent.setAction(ACTION_REFRESH);
        context.sendBroadcast(intent);
    }
}
