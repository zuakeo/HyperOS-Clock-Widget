package com.zuakeo.clockwidget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 天气缓存 + 城市设置的唯一入口。
 *
 * 用 SharedPreferences 而不是数据库：一共就 6 个字段，
 * 组件进程随时可能被回收，启动时读一次即可。
 */
final class WeatherStore {

    private static final String PREFS = "clock_weather";

    private static final String K_CITY = "city";
    private static final String K_LAT = "lat";
    private static final String K_LON = "lon";
    private static final String K_TEMP = "temp";
    private static final String K_CODE = "code";
    private static final String K_IS_DAY = "is_day";
    private static final String K_AT = "at";

    /** 首次使用、还没选城市时的默认城市（用户在北京）。 */
    static final String DEFAULT_CITY = "北京";
    static final double DEFAULT_LAT = 39.9042d;
    static final double DEFAULT_LON = 116.4074d;

    static final class Snapshot {
        final String city;
        final double lat;
        final double lon;
        final int temp;
        final int code;
        final boolean day;
        final long at;
        final boolean hasTemp;

        Snapshot(String city, double lat, double lon, int temp, int code, boolean day,
                 long at, boolean hasTemp) {
            this.city = city;
            this.lat = lat;
            this.lon = lon;
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.at = at;
            this.hasTemp = hasTemp;
        }

        boolean stale(long now, long maxAgeMs) {
            return !hasTemp || now - at > maxAgeMs;
        }
    }

    private WeatherStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Snapshot load(Context context) {
        SharedPreferences p = prefs(context);
        String city = p.getString(K_CITY, DEFAULT_CITY);
        double lat = Double.longBitsToDouble(p.getLong(K_LAT,
                Double.doubleToLongBits(DEFAULT_LAT)));
        double lon = Double.longBitsToDouble(p.getLong(K_LON,
                Double.doubleToLongBits(DEFAULT_LON)));
        int temp = p.getInt(K_TEMP, 0);
        int code = p.getInt(K_CODE, -1);
        boolean day = p.getBoolean(K_IS_DAY, true);
        long at = p.getLong(K_AT, 0L);
        boolean hasTemp = p.contains(K_TEMP);
        return new Snapshot(city, lat, lon, temp, code, day, at, hasTemp);
    }

    static void saveCity(Context context, String city, double lat, double lon) {
        SharedPreferences.Editor e = prefs(context).edit();
        e.putString(K_CITY, city);
        e.putLong(K_LAT, Double.doubleToLongBits(lat));
        e.putLong(K_LON, Double.doubleToLongBits(lon));
        // 换城市后旧的温度必须作废，否则会短暂显示上一个城市的天气。
        e.remove(K_TEMP);
        e.remove(K_CODE);
        e.remove(K_IS_DAY);
        e.remove(K_AT);
        e.apply();
    }

    static void saveNow(Context context, int temp, int code, boolean day, long at) {
        prefs(context).edit()
                .putInt(K_TEMP, temp)
                .putInt(K_CODE, code)
                .putBoolean(K_IS_DAY, day)
                .putLong(K_AT, at)
                .apply();
    }
}
