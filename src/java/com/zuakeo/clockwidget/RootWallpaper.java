package com.zuakeo.clockwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 用 root 读壁纸位图，按「屏幕上真正看得见的那一块」算亮度。
 *
 * 为什么需要它：系统 WallpaperManager.getWallpaperColors() 给的是**整张壁纸**的主色，
 * 竖屏只显示中间一条时，主色可能来自根本看不见的边缘（本机就是：主色 #603020，
 * 亮度 0.047，判成白字，但组件下面其实是人物的脸，白字糊成一片）。
 *
 * 路径：
 *   1. su -c cat /data/system/users/0/wallpaper 拿到原始位图（root）；
 *   2. 按屏幕比例居中裁切 = 屏幕上实际可见的部分；
 *   3. 取样（顶部 / 整屏 / 底部）×（10%~90% 宽）求 WCAG 相对亮度。
 *
 * 读到的位图会缓存到 cacheDir，6 小时或换壁纸时失效；
 * 没有 root / 授权被拒时返回 null，调用方回退到系统主色。
 */
final class RootWallpaper {

    private static final String TAG = "RootWallpaper";
    private static final String CACHE_NAME = "root_wallpaper.img";
    private static final long CACHE_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_BYTES = 24 * 1024 * 1024;

    private RootWallpaper() {
    }

    /** 返回 true=该用白字，false=该用深字，null=拿不到（无 root/失败）。 */
    static Boolean sample(Context context, String region) {
        Bitmap bitmap = load(context);
        if (bitmap == null) {
            return null;
        }
        try {
            // 不能用 getResources().getDisplayMetrics()：组件进程的 configuration
            // 可能是旧的（横屏启动后一直是横屏），会按错的宽高比裁切。
            android.graphics.Rect bounds = ((android.view.WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE))
                    .getCurrentWindowMetrics().getBounds();
            int screenW = Math.max(1, bounds.width());
            int screenH = Math.max(1, bounds.height());
            float targetAspect = screenW / (float) screenH;

            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int cropW;
            int cropH;
            if (w / (float) h > targetAspect) {
                cropH = h;
                cropW = Math.round(h * targetAspect);
            } else {
                cropW = w;
                cropH = Math.round(w / targetAspect);
            }
            int left = (w - cropW) / 2;
            int top = (h - cropH) / 2;

            float y0;
            float y1;
            if (WidgetSettings.R_BOTTOM.equals(region)) {
                y0 = 0.50f;
                y1 = 0.82f;
            } else if (WidgetSettings.R_FULL.equals(region)) {
                y0 = 0.08f;
                y1 = 0.92f;
            } else {
                y0 = 0.22f;
                y1 = 0.50f;
            }
            int sx = left + Math.round(cropW * 0.10f);
            int ex = left + Math.round(cropW * 0.90f);
            int sy = top + Math.round(cropH * y0);
            int ey = top + Math.round(cropH * y1);

            Bitmap patch = Bitmap.createBitmap(bitmap, sx, sy,
                    Math.max(1, ex - sx), Math.max(1, ey - sy));
            Bitmap small = Bitmap.createScaledBitmap(patch, 24, 24, true);
            if (small != patch) {
                patch.recycle();
            }
            int[] pixels = new int[24 * 24];
            small.getPixels(pixels, 0, 24, 0, 0, 24, 24);
            small.recycle();

            double sum = 0;
            for (int px : pixels) {
                sum += relativeLuminance(px);
            }
            float luminance = (float) (sum / pixels.length);
            boolean white = contrastWhite(luminance) >= contrastDark(luminance);
            Log.i(TAG, "区域取色 region=" + region + " 亮度=" + luminance
                    + " -> " + (white ? "白字" : "深字"));
            return white;
        } catch (Throwable t) {
            Log.i(TAG, "区域取色失败: " + t);
            return null;
        } finally {
            bitmap.recycle();
        }
    }

    /** 方向变了，可见的壁纸区域也变了，缓存要区分横竖。 */
    static String orientationKey(Context context) {
        try {
            android.graphics.Rect bounds = ((android.view.WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE))
                    .getCurrentWindowMetrics().getBounds();
            return bounds.width() > bounds.height() ? "L" : "P";
        } catch (Throwable t) {
            return "P";
        }
    }

    /** 换壁纸时清掉位图缓存。 */
    static void invalidate(Context context) {
        try {
            File file = new File(context.getCacheDir(), CACHE_NAME);
            if (file.exists() && !file.delete()) {
                Log.i(TAG, "旧壁纸缓存删不掉，忽略");
            }
        } catch (Throwable ignored) {
        }
    }

    private static Bitmap load(Context context) {
        File file = new File(context.getCacheDir(), CACHE_NAME);
        long now = System.currentTimeMillis();
        if (file.exists() && now - file.lastModified() < CACHE_MS) {
            Bitmap cached = decode(file);
            if (cached != null) {
                return cached;
            }
        }

        byte[] data = execBytes("cat /data/system/users/0/wallpaper");
        if (data != null && data.length > 0) {
            try {
                FileOutputStream out = new FileOutputStream(file);
                out.write(data);
                out.close();
            } catch (Throwable t) {
                Log.i(TAG, "写壁纸缓存失败: " + t);
            }
            Bitmap fresh = decode(file);
            if (fresh != null) {
                return fresh;
            }
        }
        return decode(file);
    }

    private static Bitmap decode(File file) {
        try {
            if (!file.exists() || file.length() == 0) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > 720) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            Log.i(TAG, "壁纸解码 " + (bitmap == null ? "失败"
                    : bitmap.getWidth() + "x" + bitmap.getHeight() + " sample=" + sample));
            return bitmap;
        } catch (Throwable t) {
            Log.i(TAG, "壁纸解码异常: " + t);
            return null;
        }
    }

    private static byte[] execBytes(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(false)
                    .start();
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                total += read;
                if (total > MAX_BYTES) {
                    Log.i(TAG, "壁纸超过上限，停止读取");
                    break;
                }
            }
            in.close();
            process.waitFor(15000, TimeUnit.MILLISECONDS);
            return out.toByteArray();
        } catch (Throwable t) {
            Log.i(TAG, "su 读取壁纸失败: " + t);
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    // ---------------------------------------------------------------- WCAG

    private static float contrastWhite(float luminance) {
        return 1.05f / (luminance + 0.05f);
    }

    private static float contrastDark(float luminance) {
        // #1A1A1A 的相对亮度约 0.0075
        return (luminance + 0.05f) / 0.0575f;
    }

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
