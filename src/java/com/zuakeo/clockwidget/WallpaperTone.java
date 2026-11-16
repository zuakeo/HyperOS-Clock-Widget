package com.zuakeo.clockwidget;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * 壁纸明暗判定 —— 决定组件用白字还是深色字。
 *
 * 三条路径，按优先级：
 *
 *  1) v1.3 起：设置里指定「自动（区域）」且 root 可用时，
 *     用 {@link RootWallpaper} 读壁纸位图，按屏幕可见区域算 WCAG 亮度。
 *     这是唯一能反映「组件下面到底是什么颜色」的路径。
 *  2) 系统壁纸主色 getWallpaperColors()（无权限）—— 智能但只代表整张壁纸；
 *     竖屏裁切后可能完全对不上（本机主色来自看不见的边缘）。
 *  3) 兜底按系统深浅色模式推断。
 *
 * 白/深的判据不再是「亮度 < 0.5」，而是白字与深字谁的 WCAG 对比度高：
 * 交叉点大约在亮度 0.197。
 */
final class WallpaperTone {

    private static final String TAG = "ClockTone";
    private static final String CACHE_PREFS = "tone_cache";
    private static final String K_WHITE = "white";
    private static final String K_AT = "at";
    private static final String K_KEY = "key";
    private static final long CACHE_MS = 6L * 60L * 60L * 1000L;

    /** darkBackground=true 表示壁纸偏暗、要用白字。 */
    static final class Result {
        final boolean darkBackground;
        final int primaryColor;

        Result(boolean darkBackground, int primaryColor) {
            this.darkBackground = darkBackground;
            this.primaryColor = primaryColor;
        }
    }

    private WallpaperTone() {
    }

    /** 老组件（v1.0）用：系统主色。 */
    static Result resolve(Context context) {
        return global(context);
    }

    /** 新组件用：按设置决定取色方式。 */
    static Result resolve(Context context, WidgetSettings settings) {
        if (WidgetSettings.C_WHITE.equals(settings.textColor)) {
            return new Result(true, 0xffffffff);
        }
        if (WidgetSettings.C_DARK.equals(settings.textColor)) {
            return new Result(false, 0xff1a1a1a);
        }
        if (WidgetSettings.C_GLOBAL.equals(settings.textColor)) {
            return global(context);
        }

        // 自动（区域）：先看缓存，再 root 取样，失败回退系统主色。
        String key = settings.textColor + "|" + settings.region + "|"
                + RootWallpaper.orientationKey(context);
        Boolean cached = readCache(context, key);
        if (cached != null) {
            return new Result(cached, cached ? 0xffffffff : 0xff1a1a1a);
        }
        Boolean sampled = RootWallpaper.sample(context, settings.region);
        if (sampled != null) {
            writeCache(context, key, sampled);
            return new Result(sampled, sampled ? 0xffffffff : 0xff1a1a1a);
        }
        android.util.Log.i(TAG, "区域取色不可用，回退壁纸全局主色");
        return global(context);
    }

    /** 换壁纸时清缓存。 */
    static void clearCache(Context context) {
        try {
            prefs(context).edit().clear().apply();
        } catch (Throwable ignored) {
        }
        RootWallpaper.invalidate(context);
    }

    private static Boolean readCache(Context context, String key) {
        try {
            SharedPreferences p = prefs(context);
            long at = p.getLong(K_AT, 0L);
            if (key.equals(p.getString(K_KEY, ""))
                    && System.currentTimeMillis() - at < CACHE_MS) {
                return p.getBoolean(K_WHITE, false);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void writeCache(Context context, String key, boolean white) {
        try {
            prefs(context).edit()
                    .putString(K_KEY, key)
                    .putBoolean(K_WHITE, white)
                    .putLong(K_AT, System.currentTimeMillis())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
    }

    /** 系统主色路径（无 root 也能用）。 */
    private static Result global(Context context) {
        // 路径 1：真实壁纸位图采样（最准，但需要 MANAGE_EXTERNAL_STORAGE）。
        try {
            Drawable drawable = WallpaperManager.getInstance(context).getDrawable();
            if (drawable instanceof BitmapDrawable) {
                android.graphics.Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    float luminance = averageLuminance(bitmap);
                    int dominant = dominantColor(bitmap);
                    android.util.Log.i(TAG, "取色路径=bitmap 亮度=" + luminance);
                    return new Result(preferWhite(luminance), dominant);
                }
            }
            android.util.Log.i(TAG, "取色路径=bitmap 但没有位图");
        } catch (Throwable t) {
            android.util.Log.i(TAG, "取色路径=bitmap 失败: " + t.getClass().getSimpleName()
                    + " " + t.getMessage());
            // Android 14+ 无权限 -> 走路径 2。
        }

        // 路径 2：系统壁纸主色（主路径，无权限要求）。
        try {
            WallpaperColors colors = WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors != null) {
                android.graphics.Color primaryColor = colors.getPrimaryColor();
                if (primaryColor != null) {
                    int primary = primaryColor.toArgb();
                    int hints = colors.getColorHints();
                    boolean supportsDarkText = (hints & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
                    float luminance = relativeLuminance(primary);
                    android.util.Log.i(TAG, "取色路径=colors 主色=" + Integer.toHexString(primary)
                            + " 亮度=" + luminance + " supportsDarkText=" + supportsDarkText);
                    boolean white = supportsDarkText || preferWhite(luminance);
                    return new Result(white, primary);
                }
                android.util.Log.i(TAG, "取色路径=colors 但主色为 null");
            } else {
                android.util.Log.i(TAG, "取色路径=colors 返回 null");
            }
        } catch (Throwable t) {
            android.util.Log.i(TAG, "取色路径=colors 失败: " + t.getClass().getSimpleName()
                    + " " + t.getMessage());
        }

        // 兜底：按系统深浅色模式推断。系统是深色 -> 壁纸多半也深 -> 白字。
        boolean night = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        android.util.Log.i(TAG, "取色路径=兜底 night=" + night);
        return new Result(!night, Color.DKGRAY);
    }

    /** 白字与深字谁的 WCAG 对比度高。 */
    private static boolean preferWhite(float luminance) {
        float whiteContrast = 1.05f / (luminance + 0.05f);
        float darkContrast = (luminance + 0.05f) / 0.0575f;
        return whiteContrast >= darkContrast;
    }

    /** 缩略后求平均相对亮度（0=黑 1=白）。 */
    private static float averageLuminance(android.graphics.Bitmap source) {
        int w = 32;
        int h = 32;
        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(source, w, h, true);
        try {
            int[] pixels = new int[w * h];
            scaled.getPixels(pixels, 0, w, 0, 0, w, h);
            double sum = 0;
            int count = 0;
            for (int px : pixels) {
                if (Color.alpha(px) < 16) {
                    continue;
                }
                sum += relativeLuminance(px);
                count++;
            }
            return count == 0 ? 0.5f : (float) (sum / count);
        } finally {
            if (scaled != source) {
                scaled.recycle();
            }
        }
    }

    /** 取出现频次最高的量化颜色，作为该壁纸的代表色。 */
    private static int dominantColor(android.graphics.Bitmap source) {
        int w = 32;
        int h = 32;
        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(source, w, h, true);
        try {
            int[] pixels = new int[w * h];
            scaled.getPixels(pixels, 0, w, 0, 0, w, h);
            java.util.HashMap<Integer, Integer> counter = new java.util.HashMap<>();
            java.util.HashMap<Integer, Integer> sums = new java.util.HashMap<>();
            for (int px : pixels) {
                if (Color.alpha(px) < 16) {
                    continue;
                }
                int key = (quantize(Color.red(px)) << 16)
                        | (quantize(Color.green(px)) << 8)
                        | quantize(Color.blue(px));
                Integer count = counter.get(key);
                counter.put(key, count == null ? 1 : count + 1);
                Integer sum = sums.get(key);
                sums.put(key, sum == null ? px : sum + px);
            }
            int bestKey = 0;
            int bestCount = -1;
            for (java.util.Map.Entry<Integer, Integer> entry : counter.entrySet()) {
                if (entry.getValue() > bestCount) {
                    bestCount = entry.getValue();
                    bestKey = entry.getKey();
                }
            }
            if (bestCount <= 0) {
                return Color.DKGRAY;
            }
            int raw = sums.get(bestKey);
            return Color.rgb(Color.red(raw), Color.green(raw), Color.blue(raw));
        } finally {
            if (scaled != source) {
                scaled.recycle();
            }
        }
    }

    private static int quantize(int v) {
        return (v >> 5) << 5;
    }

    /** WCAG 相对亮度。 */
    private static float relativeLuminance(int color) {
        double r = channel(Color.red(color));
        double g = channel(Color.green(color));
        double b = channel(Color.blue(color));
        return (float) (0.2126d * r + 0.7152d * g + 0.0722d * b);
    }

    private static double channel(int v) {
        double s = v / 255.0d;
        return s <= 0.03928d ? s / 12.92d : Math.pow((s + 0.055d) / 1.055d, 2.4d);
    }
}
