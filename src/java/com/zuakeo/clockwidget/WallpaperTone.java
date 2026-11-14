package com.zuakeo.clockwidget;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * 壁纸明暗判定 —— 决定组件用深色字还是浅色字。
 *
 * 两条取色路径（按可用性依次尝试）：
 *
 *  1) WallpaperManager.getDrawable()：拿真实壁纸位图，缩略后求平均亮度，最准。
 *     但 Android 14 起该接口要求 MANAGE_EXTERNAL_STORAGE，否则必定抛 SecurityException
 *     （AOSP 注释：From Android 14, this method should not be used and will always
 *     throw a SecurityException）。本 App 不申请该权限，所以这条路径在
 *     Android 14+ 上会失败并落到路径 2。
 *
 *  2) WallpaperManager.getWallpaperColors()：系统自己算好的壁纸主色，
 *     无权限要求，Android 12+ 都可用，是本 App 的主路径。
 *     它还带 HINT_SUPPORTS_DARK_TEXT 提示，直接说明壁纸是否够浅、能否用深色字。
 *
 * 两条都拿不到时（极少见），按系统深浅色模式推断。
 */
final class WallpaperTone {

    private static final String TAG = "ClockTone";

    /** 壁纸偏暗 -> 用白色字；壁纸偏亮 -> 用深色字。 */
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

    static Result resolve(Context context) {
        // 路径 1：真实壁纸位图采样（最准，但需要 MANAGE_EXTERNAL_STORAGE）。
        try {
            Drawable drawable = WallpaperManager.getInstance(context).getDrawable();
            if (drawable instanceof BitmapDrawable) {
                android.graphics.Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    float luminance = averageLuminance(bitmap);
                    int dominant = dominantColor(bitmap);
                    android.util.Log.i(TAG, "取色路径=bitmap 亮度=" + luminance);
                    return new Result(luminance < 0.5f, dominant);
                }
            }
            android.util.Log.i(TAG, "取色路径=bitmap 但没有位图");
        } catch (Throwable t) {
            android.util.Log.i(TAG, "取色路径=bitmap 失败: " + t.getClass().getSimpleName()
                    + " " + t.getMessage());
            // Android 14+ 无权限 -> 走路径 2。
        }

        // 路径 2：系统壁纸主色（主路径，无权限要求）。
        // 注意：getWallpaperColors() 内部会调 StrictMode.assertUiContext，
        // 在非 UI 上下文里调用可能被判违规（默认只记日志）。这里全程 try/catch 兜住，
        // 任何异常都退到兜底分支，不会让组件空白。
        try {
            WallpaperColors colors = WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (colors != null) {
                // 注意：主色/次色/三色都可能是 null（探测中实测到过 NPE），
                // 取色前必须逐个判空，否则整条路径会被一个 null 打断。
                android.graphics.Color primaryColor = colors.getPrimaryColor();
                if (primaryColor != null) {
                    int primary = primaryColor.toArgb();
                    int hints = colors.getColorHints();
                    // 系统明确说壁纸够浅、可以用深色字 -> 直接采信。
                    boolean supportsDarkText = (hints & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0;
                    float luminance = relativeLuminance(primary);
                    android.util.Log.i(TAG, "取色路径=colors 主色=" + Integer.toHexString(primary)
                            + " 亮度=" + luminance + " supportsDarkText=" + supportsDarkText);
                    boolean dark = supportsDarkText ? false : luminance < 0.5f;
                    return new Result(dark, primary);
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
