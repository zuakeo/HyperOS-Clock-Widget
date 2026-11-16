package com.zuakeo.clockwidget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 天气与城市搜索，全部走 Open-Meteo 的公开接口：
 *
 *  - 天气：https://api.open-meteo.com/v1/forecast
 *  - 城市：https://geocoding-api.open-meteo.com/v1/search
 *
 * 两者都**不需要 API Key**，只需要 INTERNET 权限。
 * 解析用 android.jar 自带的 org.json，不引入任何依赖。
 */
final class WeatherClient {

    private static final String TAG = "ClockWeather";

    private static final String URL_FORECAST =
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,weather_code,is_day&timezone=auto";
    private static final String URL_GEOCODE =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=6&language=zh&format=json";

    static final class Now {
        final int tempC;
        final int code;
        final boolean day;

        Now(int tempC, int code, boolean day) {
            this.tempC = tempC;
            this.code = code;
            this.day = day;
        }
    }

    static final class Place {
        final String name;
        final String admin;
        final String country;
        final double lat;
        final double lon;

        Place(String name, String admin, String country, double lat, double lon) {
            this.name = name;
            this.admin = admin;
            this.country = country;
            this.lat = lat;
            this.lon = lon;
        }

        String subtitle() {
            StringBuilder sb = new StringBuilder();
            if (admin != null && admin.length() > 0 && !admin.equals(name)) {
                sb.append(admin);
            }
            if (country != null && country.length() > 0) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(country);
            }
            return sb.toString();
        }
    }

    private WeatherClient() {
    }

    /** 取当前温度与 WMO 天气代码。失败抛异常，由调用方决定显示缓存还是占位符。 */
    static Now current(double lat, double lon) throws Exception {
        String url = String.format(java.util.Locale.US, URL_FORECAST,
                trim(lat), trim(lon));
        JSONObject root = new JSONObject(get(url));
        JSONObject current = root.optJSONObject("current");
        if (current == null) {
            throw new IllegalStateException("响应里没有 current 字段");
        }
        double t = current.getDouble("temperature_2m");
        int code = current.optInt("weather_code", -1);
        boolean day = current.optInt("is_day", 1) != 0;
        return new Now((int) Math.round(t), code, day);
    }

    /** 按名字搜城市，返回候选列表（可能为空）。 */
    static List<Place> search(String keyword) throws Exception {
        String url = String.format(URL_GEOCODE,
                URLEncoder.encode(keyword, "UTF-8"));
        JSONObject root = new JSONObject(get(url));
        JSONArray results = root.optJSONArray("results");
        List<Place> places = new ArrayList<>();
        if (results == null) {
            return places;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String name = o.optString("name", "");
            if (name.length() == 0) {
                continue;
            }
            places.add(new Place(
                    name,
                    o.optString("admin1", ""),
                    o.optString("country", ""),
                    o.optDouble("latitude", 0d),
                    o.optDouble("longitude", 0d)));
        }
        return places;
    }

    private static String trim(double v) {
        return String.format(java.util.Locale.US, "%.5f", v);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "ClockWidget/1.1 (Android)");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), Charset.forName("UTF-8"));
            } finally {
                in.close();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** WMO 天气代码 -> 图标键；晴、多云、雾再按昼夜区分。 */
    static String iconKey(int code, boolean day) {
        switch (code) {
            case 0:
                return day ? "sunny" : "sunny_night";
            case 1:
            case 2:
                return day ? "cloudy" : "cloudy_night";
            case 3:
                return "overcast";
            case 45:
            case 48:
                return day ? "fog" : "fog_night";
            case 51:
            case 53:
            case 55:
            case 61:
            case 80:
                return "light_rain";
            case 56:
            case 57:
            case 66:
            case 67:
                return "ice_rain";
            case 63:
            case 81:
                return "moderate_rain";
            case 65:
            case 82:
                return "heavy_rain";
            case 71:
            case 77:
            case 85:
                return "light_snow";
            case 73:
                return "moderate_snow";
            case 75:
            case 86:
                return "heavy_snow";
            case 79:
                return "rain_snow";
            case 95:
            case 96:
            case 99:
                return "storm";
            case 30:
            case 35:
                return "sand";
            default:
                return "overcast";
        }
    }

    /**
     * 图标资源解析：
     *  - 先找 miui_wx_*（可选的本地图标，不进仓库）；
     *  - 再找仓库自带的 ic_wx_*（扁平风矢量图）；
     *  - 都没有就退回 overcast。
     */
    static int iconRes(android.content.Context context, String key) {
        Integer cached = ICONS.get(key);
        if (cached != null) {
            return cached;
        }
        int res = 0;
        try {
            res = context.getResources().getIdentifier(
                    "miui_wx_" + key, "drawable", context.getPackageName());
            if (res == 0) {
                res = context.getResources().getIdentifier(
                        "ic_wx_" + key, "drawable", context.getPackageName());
            }
        } catch (Throwable ignored) {
        }
        if (res == 0) {
            res = R.drawable.ic_wx_overcast;
        }
        ICONS.put(key, res);
        return res;
    }

    private static final java.util.HashMap<String, Integer> ICONS = new java.util.HashMap<>();

    static void log(String message) {
        Log.i(TAG, message);
    }
}
