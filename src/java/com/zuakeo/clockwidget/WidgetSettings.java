package com.zuakeo.clockwidget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 「桌面时钟 · 天气」组件的显示设置。
 *
 * 单独一份 SharedPreferences，和天气缓存分开，互不影响。
 * 设置页每次改动都写这里，然后广播 RERENDER 让组件按新设置重排。
 *
 * 注意：字重不能运行时改 —— fontFamily 是布局级属性，RemoteViews 没有对应方法，
 * 所以按 4 档字重预生成 4 套布局（见 tools/gen_widget_layouts.py）。
 */
final class WidgetSettings {

    private static final String PREFS = "widget_settings";

    static final String K_DATE = "show_date";
    static final String K_WEEKDAY = "show_weekday";
    static final String K_LUNAR = "show_lunar";
    static final String K_ICON = "show_icon";
    static final String K_TEMP = "show_temp";
    static final String K_CITY = "show_city";
    static final String K_SECONDS = "show_seconds";
    static final String K_FAHRENHEIT = "use_fahrenheit";
    static final String K_TIME_SIZE = "time_size";
    static final String K_DATE_SIZE = "date_size";
    static final String K_WEIGHT = "time_weight";

    static final String W_LIGHT = "light";
    static final String W_NORMAL = "normal";
    static final String W_MEDIUM = "medium";
    static final String W_BOLD = "bold";

    static final int TIME_SIZE_MIN = 36;
    static final int TIME_SIZE_MAX = 110;
    static final int DATE_SIZE_MIN = 10;
    static final int DATE_SIZE_MAX = 26;

    final boolean showDate;
    final boolean showWeekday;
    final boolean showLunar;
    final boolean showIcon;
    final boolean showTemp;
    final boolean showCity;
    final boolean showSeconds;
    final boolean fahrenheit;
    final int timeSizeSp;
    final int dateSizeSp;
    final String weight;

    private WidgetSettings(boolean showDate, boolean showWeekday, boolean showLunar,
                           boolean showIcon, boolean showTemp, boolean showCity,
                           boolean showSeconds, boolean fahrenheit,
                           int timeSizeSp, int dateSizeSp, String weight) {
        this.showDate = showDate;
        this.showWeekday = showWeekday;
        this.showLunar = showLunar;
        this.showIcon = showIcon;
        this.showTemp = showTemp;
        this.showCity = showCity;
        this.showSeconds = showSeconds;
        this.fahrenheit = fahrenheit;
        this.timeSizeSp = timeSizeSp;
        this.dateSizeSp = dateSizeSp;
        this.weight = weight;
    }

    static WidgetSettings load(Context context) {
        SharedPreferences p = prefs(context);
        return new WidgetSettings(
                p.getBoolean(K_DATE, true),
                p.getBoolean(K_WEEKDAY, true),
                p.getBoolean(K_LUNAR, true),
                p.getBoolean(K_ICON, true),
                p.getBoolean(K_TEMP, true),
                p.getBoolean(K_CITY, false),
                p.getBoolean(K_SECONDS, false),
                p.getBoolean(K_FAHRENHEIT, false),
                clamp(p.getInt(K_TIME_SIZE, 62), TIME_SIZE_MIN, TIME_SIZE_MAX),
                clamp(p.getInt(K_DATE_SIZE, 15), DATE_SIZE_MIN, DATE_SIZE_MAX),
                normalizeWeight(p.getString(K_WEIGHT, W_MEDIUM)));
    }

    static void putBool(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    static boolean getBool(Context context, String key, boolean defValue) {
        return prefs(context).getBoolean(key, defValue);
    }

    static int getInt(Context context, String key, int defValue) {
        return prefs(context).getInt(key, defValue);
    }

    static String getString(Context context, String key, String defValue) {
        return prefs(context).getString(key, defValue);
    }

    static void putInt(Context context, String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    static void putString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }

    /** 通知组件按新设置重排（不联网）。 */
    static void notifyChanged(Context context) {
        ClockWeatherWidgetProvider.requestRerender(context);
    }

    static String normalizeWeight(String value) {
        if (W_LIGHT.equals(value) || W_NORMAL.equals(value)
                || W_BOLD.equals(value) || W_MEDIUM.equals(value)) {
            return value;
        }
        return W_MEDIUM;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
