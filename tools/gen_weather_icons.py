#!/usr/bin/env python3
# 生成天气图标（白色 / 深色两套 VectorDrawable）。
# 这些图标只用线条，24x24 视口，1.7 描边，缩到 15dp 仍然清楚。
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "res", "drawable")

CIRCLES = {
    "clear": "M12,6.4a5.6,5.6 0 1,1 -0.01,0z",
}

# 每个状态由若干 (pathData, 是否填充) 组成
STATES = {
    "clear": [
        ("M12,6.4a5.6,5.6 0 1,1 -0.01,0z", False),
        ("M12,1.6L12,3.4", False),
        ("M12,20.6L12,22.4", False),
        ("M1.6,12L3.4,12", False),
        ("M20.6,12L22.4,12", False),
        ("M4.7,4.7L6.0,6.0", False),
        ("M18.0,18.0L19.3,19.3", False),
        ("M19.3,4.7L18.0,6.0", False),
        ("M6.0,18.0L4.7,19.3", False),
    ],
    # 晴（夜间）：弯月，实心
    "clear_night": [
        ("M20.8,13.2A8.8,8.8 0 1,1 10.8,3.2 6.9,6.9 0 0,0 20.8,13.2Z", True),
    ],
    "partly_night": [
        ("M18.6,9.6A4.6,4.6 0 1,1 12.0,3.4 3.7,3.7 0 0,0 18.6,9.6Z", True),
        ("M8.4,20.2h7.6c1.8,0 3.2,-1.4 3.2,-3.2 0,-1.8 -1.4,-3.2 -3.2,-3.2 -0.3,0 -0.6,0 -0.8,0.1C14.6,12.4 13.2,11.4 11.5,11.4 9.5,11.4 7.9,12.8 7.7,14.7 6.1,15.0 4.9,16.4 4.9,18.1c0,1.2 1.0,2.1 2.2,2.1z", False),
    ],
    "partly": [
        ("M8.3,4.0a3.3,3.3 0 1,1 -0.01,0z", False),
        ("M8.3,1.7L8.3,2.6", False),
        ("M3.4,7.3L4.3,7.3", False),
        ("M4.8,3.8L5.4,4.4", False),
        ("M11.8,3.8L11.2,4.4", False),
        ("M4.8,10.8L5.4,10.2", False),
        ("M8.2,18.9h8.3c1.9,0 3.4,-1.5 3.4,-3.4 0,-1.9 -1.5,-3.4 -3.4,-3.4 -0.3,0 -0.6,0 -0.9,0.1C14.9,10.4 13.3,9.2 11.4,9.2 9.3,9.2 7.6,10.8 7.4,12.8 5.7,13.1 4.4,14.5 4.4,16.2c0,1.5 1.2,2.7 2.7,2.7z", False),
    ],
    "cloudy": [
        ("M9.8,12.2h5.9c1.4,0 2.5,-1.1 2.5,-2.5 0,-1.4 -1.1,-2.5 -2.5,-2.5 -0.2,0 -0.5,0 -0.7,0.1C14.5,5.9 13.2,5 11.7,5 10.1,5 8.8,6.1 8.6,7.7 7.5,7.9 6.6,8.9 6.6,10.1c0,1.1 0.9,2.0 2.0,2.0z", False),
        ("M7.8,19.1h9.4c1.8,0 3.2,-1.4 3.2,-3.2 0,-1.8 -1.4,-3.2 -3.2,-3.2 -0.2,0 -0.5,0 -0.7,0.1C16.1,11.1 14.7,10.1 13,10.1 11,10.1 9.4,11.5 9.2,13.4 7.6,13.7 6.4,15.1 6.4,16.8c0,1.3 1.0,2.3 2.3,2.3z", False),
    ],
    "fog": [
        ("M6.4,8.4h10.4", False),
        ("M8.4,12.2h9.4", False),
        ("M5.2,16.0h10.6", False),
        ("M8.0,19.8h8.6", False),
    ],
    "rain": [
        ("M7.6,14.9h9.2c1.8,0 3.2,-1.4 3.2,-3.2 0,-1.8 -1.4,-3.2 -3.2,-3.2 -0.2,0 -0.5,0 -0.7,0.1C15.7,6.9 14.2,5.9 12.5,5.9 10.5,5.9 8.9,7.3 8.7,9.2 7.1,9.5 5.9,10.9 5.9,12.6c0,1.3 0.8,2.3 1.7,2.3z", False),
        ("M8.6,17.2L7.9,19.6", False),
        ("M12.1,17.2L11.4,19.6", False),
        ("M15.6,17.2L14.9,19.6", False),
    ],
    "snow": [
        ("M7.6,14.6h9.2c1.8,0 3.2,-1.4 3.2,-3.2 0,-1.8 -1.4,-3.2 -3.2,-3.2 -0.2,0 -0.5,0 -0.7,0.1C15.7,6.6 14.2,5.6 12.5,5.6 10.5,5.6 8.9,7.0 8.7,8.9 7.1,9.2 5.9,10.6 5.9,12.3c0,1.3 0.8,2.3 1.7,2.3z", False),
        ("M8.5,17.4L8.5,20.0", False),
        ("M7.2,18.7L9.8,18.7", False),
        ("M12.0,18.0L12.0,20.6", False),
        ("M10.7,19.3L13.3,19.3", False),
        ("M15.5,17.4L15.5,20.0", False),
        ("M14.2,18.7L16.8,18.7", False),
    ],
    "storm": [
        ("M7.6,14.2h9.2c1.8,0 3.2,-1.4 3.2,-3.2 0,-1.8 -1.4,-3.2 -3.2,-3.2 -0.2,0 -0.5,0 -0.7,0.1C15.7,6.2 14.2,5.2 12.5,5.2 10.5,5.2 8.9,6.6 8.7,8.5 7.1,8.8 5.9,10.2 5.9,11.9c0,1.3 0.8,2.3 1.7,2.3z", False),
        ("M13.4,14.6L10.2,19.2L12.4,19.2L11.2,22L15.0,17.4L12.7,17.4Z", True),
    ],
}

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- 自动生成：tools/gen_weather_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{body}
</vector>
"""

STROKE = ('    <path\n'
          '        android:fillColor="#00000000"\n'
          '        android:pathData="{d}"\n'
          '        android:strokeColor="{c}"\n'
          '        android:strokeLineCap="round"\n'
          '        android:strokeLineJoin="round"\n'
          '        android:strokeWidth="1.7" />')

FILL = ('    <path\n'
        '        android:fillColor="{c}"\n'
        '        android:pathData="{d}" />')


def write(name, color, parts):
    body = []
    for data, filled in parts:
        body.append((FILL if filled else STROKE).format(d=data, c=color))
    path = os.path.join(OUT, name + ".xml")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(TEMPLATE.format(body="\n".join(body)))
    print("written", path)


def main():
    os.makedirs(OUT, exist_ok=True)
    for state, parts in STATES.items():
        write("ic_wx_" + state, "#FFFFFFFF", parts)
        write("ic_wx_" + state + "_dark", "#FF1A1A1A", parts)


if __name__ == "__main__":
    main()
