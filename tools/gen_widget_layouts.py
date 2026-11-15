#!/usr/bin/env python3
# 生成 8 套组件布局：文字色（白/深） × 时间字重（细/常规/中等/粗）。
# fontFamily 是布局级属性，RemoteViews 改不了，所以只能每种字重预生成一套。
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "res", "layout")

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 自动生成：tools/gen_widget_layouts.py（文字色=__TONE_DESC__，时间字重=__WEIGHT_DESC__） -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/ww_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center_vertical|start"
    android:paddingStart="16dp"
    android:paddingEnd="16dp">

    <LinearLayout
        android:id="@+id/ww_date_touch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingTop="7dp"
        android:paddingBottom="3dp">

        <TextClock
            android:id="@+id/ww_date"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif"
            android:format24Hour="@string/date_format_compact"
            android:format12Hour="@string/date_format_compact"
            android:includeFontPadding="false"
            android:shadowColor="__SHADOW__"
            android:shadowDx="0"
            android:shadowDy="1"
            android:shadowRadius="3"
            android:textColor="__DATE__"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/ww_lunar"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="7dp"
            android:fontFamily="sans-serif"
            android:includeFontPadding="false"
            android:shadowColor="__SHADOW__"
            android:shadowDx="0"
            android:shadowDy="1"
            android:shadowRadius="3"
            android:textColor="__LUNAR__"
            android:textSize="14sp"
            android:text="九月三十" />

        <LinearLayout
            android:id="@+id/ww_weather_touch"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingTop="4dp"
            android:paddingBottom="4dp">

            <ImageView
                android:id="@+id/ww_icon"
                android:layout_width="17dp"
                android:layout_height="17dp"
                android:scaleType="fitCenter"
                android:src="@drawable/__ICON__" />

            <TextView
                android:id="@+id/ww_temp"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="3dp"
                android:fontFamily="sans-serif"
                android:includeFontPadding="false"
                android:shadowColor="__SHADOW__"
                android:shadowDx="0"
                android:shadowDy="1"
                android:shadowRadius="3"
                android:textColor="__TEMP__"
                android:textSize="15sp"
                android:text="--°" />
        </LinearLayout>

        <TextView
            android:id="@+id/ww_city"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:ellipsize="end"
            android:fontFamily="sans-serif"
            android:includeFontPadding="false"
            android:maxLines="1"
            android:shadowColor="__SHADOW__"
            android:shadowDx="0"
            android:shadowDy="1"
            android:shadowRadius="3"
            android:textColor="__CITY__"
            android:textSize="13sp"
            android:visibility="gone" />
    </LinearLayout>

    <FrameLayout
        android:id="@+id/ww_time_touch"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="0dp"
        android:paddingBottom="6dp">

        <TextClock
            android:id="@+id/ww_time"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFamily="__FONT__"__STYLE__
            android:format24Hour="HH:mm"
            android:format12Hour="h:mm"
            android:includeFontPadding="false"
            android:shadowColor="__SHADOW__"
            android:shadowDx="0"
            android:shadowDy="1"
            android:shadowRadius="4"
            android:textColor="__TIME__"
            android:textSize="62sp" />
    </FrameLayout>

</LinearLayout>
"""

WHITE = dict(
    desc="白字（壁纸偏暗）",
    prefix="ww_white",
    icon="ic_wx_clear",
    date="#F2FFFFFF", lunar="#D9FFFFFF", temp="#F2FFFFFF",
    city="#B3FFFFFF", time="#FFFFFFFF", shadow="#59000000",
)

BLACK = dict(
    desc="深字（壁纸偏亮）",
    prefix="ww_black",
    icon="ic_wx_clear_dark",
    date="#F21A1A1A", lunar="#CC1A1A1A", temp="#F21A1A1A",
    city="#991A1A1A", time="#FF1A1A1A", shadow="#4DFFFFFF",
)

WEIGHTS = {
    "light": ("细", "sans-serif-light", ""),
    "normal": ("常规", "sans-serif", ""),
    "medium": ("中等", "sans-serif-medium", ""),
    "bold": ("粗", "sans-serif-medium", '\n            android:textStyle="bold"'),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for tone in (WHITE, BLACK):
        for key, (desc, font, style) in WEIGHTS.items():
            xml = TEMPLATE
            for token, value in {
                "__TONE_DESC__": tone["desc"],
                "__WEIGHT_DESC__": desc,
                "__SHADOW__": tone["shadow"],
                "__DATE__": tone["date"],
                "__LUNAR__": tone["lunar"],
                "__TEMP__": tone["temp"],
                "__CITY__": tone["city"],
                "__TIME__": tone["time"],
                "__ICON__": tone["icon"],
                "__FONT__": font,
                "__STYLE__": style,
            }.items():
                xml = xml.replace(token, value)
            path = os.path.join(OUT, "%s_%s.xml" % (tone["prefix"], key))
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(xml)
            print("written", path)


if __name__ == "__main__":
    main()
