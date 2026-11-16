package com.zuakeo.clockwidget;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** 快捷设置磁贴：隐藏桌面图标后用它进设置。 */
public class ClockTileService extends TileService {

    @Override
    public void onClick() {
        Intent intent = new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pending);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Throwable t) {
            try {
                startActivity(intent);
            } catch (Throwable ignored) {
            }
        }
    }
}
