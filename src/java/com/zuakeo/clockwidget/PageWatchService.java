package com.zuakeo.clockwidget;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 盯桌面当前页有没有本应用的组件，结果写进 ClockFlag 组件状态（模块在 SystemUI 里读它）。
 *
 * 为什么要绕无障碍：HyperOS 4 的桌面是 Flutter 实现，APK 里没有 dex；LSPosed 2.x 对它的
 * 「HyperOS Runtime」只加载原生 .so 模块，普通 dex 模块根本注入不进去（实测日志里只有
 * HyperCeiler / HomeTweaks 的 native module）。所以只能从外面看屏上的节点树：
 * 桌面当前页有时钟组件时，无障碍树里会有 package=com.zuakeo.clockwidget 的节点；
 * 翻到别的页就没了。
 *
 * 只监听 com.miui.home 的窗口/内容/滚动事件，去抖 250ms，读一次根节点做判断。
 */
public class PageWatchService extends AccessibilityService {

    private static final String HOME = "com.miui.home";
    private static final long DEBOUNCE_MS = 250L;
    /**
     * 桌面把组件在无障碍树里合并成一个节点：package 是 com.miui.home，
     * contentDescription 是组件的 label（两个组件都以「桌面时钟」开头），
     * 内部的 RemoteViews 不展开。所以按 label 前缀认，别指望包名。
     */
    private static final String[] LABELS = {"桌面时钟"};

    private static void log(String s) {
        android.util.Log.i("PageWatch", s);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect rect = new Rect();
    private Boolean last;

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            evaluate();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            evaluate();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.packageNames = new String[]{HOME};
        info.notificationTimeout = 100;
        setServiceInfo(info);
        // 一开始不知道状态，先按「没有」处理，避免状态栏时间莫名消失。
        publish(false);
        // 兜底：偶尔会漏事件（桌面是 Flutter），每 3 秒自己看一眼。
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 3000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        handler.removeCallbacks(check);
        handler.postDelayed(check, DEBOUNCE_MS);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(check);
        handler.removeCallbacks(tick);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(check);
        handler.removeCallbacks(tick);
        publish(false);
        super.onDestroy();
    }

    /** 当前屏幕上的窗口里有没有本应用的组件节点。 */
    private void evaluate() {
        boolean visible = false;
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                if (w == null || w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                if (!HOME.equals(String.valueOf(root.getPackageName()))) {
                    continue;
                }
                visible = containsOurWidget(root);
                if (visible) {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        publish(visible);
    }

    private boolean containsOurWidget(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            if (isWidgetNode(node)) {
                return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                boolean hit = containsOurWidget(child);
                child.recycle();
                if (hit) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 组件节点：包名是我们自己，或 contentDescription / text 以组件 label 开头。 */
    private boolean isWidgetNode(AccessibilityNodeInfo node) {
        CharSequence pkg = node.getPackageName();
        boolean ours = pkg != null && ClockFlag.PKG.equals(pkg.toString());
        if (!ours) {
            CharSequence desc = node.getContentDescription();
            CharSequence text = node.getText();
            ours = startsWithLabel(desc) || startsWithLabel(text);
        }
        if (!ours || !node.isVisibleToUser()) {
            return false;
        }
        node.getBoundsInScreen(rect);
        return rect.width() > 32 && rect.height() > 32;
    }

    private static boolean startsWithLabel(CharSequence v) {
        if (v == null) {
            return false;
        }
        String s = v.toString();
        for (String l : LABELS) {
            if (s.startsWith(l)) {
                return true;
            }
        }
        return false;
    }

    private void publish(boolean onPage) {
        if (last != null && last == onPage) {
            return;
        }
        last = onPage;
        log("桌面当前页有组件=" + onPage);
        ClockFlag.setOnPage(this, onPage);
    }
}
