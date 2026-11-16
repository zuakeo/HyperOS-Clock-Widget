#!/usr/bin/env python3
# 生成扁平风格天气图标（白色云 + 黄色太阳/月亮），同时输出 HTML 预览。
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "res", "drawable")
PREVIEW = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "build", "icon_preview.html")

SUN = "#FFC733"
MOON = "#FFC733"
CLOUD = "#FFFFFF"
CLOUD_EDGE = "#33000000"
BACK_CLOUD = "#C3D0DE"
RAIN = "#4A9EF8"
ICE = "#7FC4FF"
SNOW = "#8FD3FF"
BOLT = "#FFC733"
FOG = "#B9C6D4"
SAND = "#D8B26A"

CLOUD_PATH = ("M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 "
              "2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 "
              "0,-2.64 -2.05,-4.78 -4.65,-4.96z")
SUN_DISC = "M12,6.8a5.2,5.2 0 1,0 0,10.4a5.2,5.2 0 1,0 0,-10.4z"
SUN_RAYS = ("M12,4.8L12,2.2M12,19.2L12,21.8M4.8,12L2.2,12M19.2,12L21.8,12"
            "M6.9,6.9L5.1,5.1M17.1,17.1L18.9,18.9M18.9,5.1L17.1,6.9M5.1,18.9L6.9,17.1")
NONE = "#00000000"
MOON_PATH = "M20.8,13.2A8.8,8.8 0 1,1 10.8,3.2 6.9,6.9 0 0,0 20.8,13.2Z"
MOON_SMALL = "M18.6,9.6A4.6,4.6 0 1,1 12.0,3.4 3.7,3.7 0 0,0 18.6,9.6Z"
BOLT_PATH = "M12.9,13.6l-3.7,5.6h2.6l-1.3,2.9 4.5,-6.1h-2.8l1.6,-2.4z"


def disc(cx, cy, r):
    return "M%.2f,%.2fa%.2f,%.2f 0 1,0 0,%.2fa%.2f,%.2f 0 1,0 0,-%.2fz" % (
        cx, cy - r, r, r, 2 * r, r, r, 2 * r)


def drop(cx, top):
    return ("M%.2f,%.2fc-0.9,1.2 -1.4,2.1 -1.4,2.8a1.4,1.4 0 0,0 2.8,0"
            "c0,-0.7 -0.5,-1.6 -1.4,-2.8z" % (cx, top))


def flake(cx, cy):
    return ("M%.2f,%.2fL%.2f,%.2fM%.2f,%.2fL%.2f,%.2fM%.2f,%.2fL%.2f,%.2f" % (
        cx, cy - 1.3, cx, cy + 1.3,
        cx - 1.12, cy - 0.65, cx + 1.12, cy + 0.65,
        cx + 1.12, cy - 0.65, cx - 1.12, cy + 0.65))


def bar(x1, y1, x2, y2):
    return "M%.1f,%.1fL%.1f,%.1f" % (x1, y1, x2, y2)


def scene(*items):
    """item: (path, color, stroke, width, scale, tx, ty)"""
    return list(items)


def el(path, color, stroke=None, width=0.0, scale=1.0, tx=0.0, ty=0.0):
    return dict(path=path, color=color, stroke=stroke, width=width,
                scale=scale, tx=tx, ty=ty)


def cloud(scale=0.82, ty=2.6, color=CLOUD, stroke=CLOUD_EDGE, width=0.7):
    return el(CLOUD_PATH, color, stroke, width, scale, 0, ty)


CLOUDS = {
    "sunny": scene(el(SUN_DISC, SUN), el(SUN_RAYS, NONE, SUN, 2.0)),
    "sunny_night": scene(el(MOON_PATH, MOON, None, 0, 0.95, 0, 0)),
    "cloudy": scene(el(disc(16.6, 7.0, 4.3), SUN),
                    cloud(0.80, 2.6)),
    "cloudy_night": scene(el(MOON_SMALL, MOON, None, 0, 0.9, 2.2, -1.6),
                          cloud(0.80, 2.6)),
    "overcast": scene(el(CLOUD_PATH, BACK_CLOUD, None, 0, 0.62, 7.0, -1.4),
                      cloud(0.86, 3.4)),
    "fog": scene(cloud(0.86, -1.4),
                 el(bar(5, 19.4, 19, 19.4), NONE, FOG, 1.5),
                 el(bar(7.2, 22.0, 16.8, 22.0), NONE, FOG, 1.5)),
    "fog_night": scene(el(MOON_SMALL, MOON, None, 0, 0.8, 3.0, -2.8),
                       cloud(0.86, -1.2),
                       el(bar(5, 19.4, 19, 19.4), NONE, FOG, 1.5),
                       el(bar(7.2, 22.0, 16.8, 22.0), NONE, FOG, 1.5)),
    "light_rain": scene(cloud(0.82, -2.2),
                        el(drop(9.0, 17.4), RAIN),
                        el(drop(15.0, 17.4), RAIN)),
    "moderate_rain": scene(cloud(0.82, -2.2),
                           el(drop(8.5, 17.2), RAIN),
                           el(drop(12.0, 18.2), RAIN),
                           el(drop(15.5, 17.2), RAIN)),
    "heavy_rain": scene(cloud(0.82, -2.6),
                        el(drop(7.0, 17.0), RAIN),
                        el(drop(10.6, 18.0), RAIN),
                        el(drop(14.2, 17.0), RAIN),
                        el(drop(17.6, 18.0), RAIN)),
    "ice_rain": scene(cloud(0.82, -2.2),
                      el(drop(9.0, 17.2), ICE),
                      el(flake(15.2, 19.2), NONE, SNOW, 1.1)),
    "rain_snow": scene(cloud(0.82, -2.2),
                       el(drop(9.0, 17.2), RAIN),
                       el(flake(15.2, 19.2), NONE, SNOW, 1.1)),
    "light_snow": scene(cloud(0.82, -2.2),
                        el(flake(12.0, 19.2), NONE, SNOW, 1.2)),
    "moderate_snow": scene(cloud(0.82, -2.2),
                           el(flake(9.2, 19.4), NONE, SNOW, 1.2),
                           el(flake(14.8, 19.4), NONE, SNOW, 1.2)),
    "heavy_snow": scene(cloud(0.82, -2.4),
                        el(flake(7.6, 19.0), NONE, SNOW, 1.2),
                        el(flake(12.0, 20.4), NONE, SNOW, 1.2),
                        el(flake(16.4, 19.0), NONE, SNOW, 1.2)),
    "storm": scene(cloud(0.82, -2.4),
                   el(BOLT_PATH, BOLT)),
    "sand": scene(cloud(0.82, -2.4),
                  el(disc(8.4, 19.4, 1.0), SAND),
                  el(disc(12.0, 21.0, 1.0), SAND),
                  el(disc(15.6, 19.4, 1.0), SAND)),
}


def xml_for(items):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- 自动生成：tools/gen_icon_set.py -->",
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="24dp"',
             '    android:height="24dp"',
             '    android:viewportWidth="24"',
             '    android:viewportHeight="24">']
    for item in items:
        attrs = ['android:pathData="%s"' % item["path"],
                 'android:fillColor="%s"' % item["color"]]
        if item["stroke"]:
            attrs.append('android:strokeColor="%s"' % item["stroke"])
            attrs.append('android:strokeWidth="%s"' % item["width"])
            attrs.append('android:strokeLineCap="round"')
        transformed = (item["scale"] != 1.0 or item["tx"] or item["ty"])
        if transformed:
            lines.append('    <group android:pivotX="12" android:pivotY="12"')
            lines.append('        android:scaleX="%s" android:scaleY="%s"' % (item["scale"], item["scale"]))
            lines.append('        android:translateX="%s" android:translateY="%s">' % (item["tx"], item["ty"]))
            lines.append('        <path %s />' % " ".join(attrs))
            lines.append('    </group>')
        else:
            lines.append('    <path %s />' % " ".join(attrs))
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def svg_for(items, size=72):
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
             'viewBox="0 0 24 24">' % (size, size)]
    for item in items:
        transform = ""
        if item["scale"] != 1.0 or item["tx"] or item["ty"]:
            transform = ' transform="translate(%s,%s) translate(12,12) scale(%s) translate(-12,-12)"' % (
                item["tx"], item["ty"], item["scale"])
        stroke = (' stroke="%s" stroke-width="%s" stroke-linecap="round"'
                  % (item["stroke"], item["width"])) if item["stroke"] else ""
        parts.append('<path d="%s" fill="%s"%s%s/>' % (item["path"], item["color"], stroke, transform))
    parts.append("</svg>")
    return "".join(parts)


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    for name, items in CLOUDS.items():
        with open(os.path.join(OUT, "ic_wx_%s.xml" % name), "w",
                  encoding="utf-8", newline="\n") as f:
            f.write(xml_for(items))
        print("written ic_wx_%s.xml" % name)

    cells = []
    for name, items in CLOUDS.items():
        cells.append('<div class="cell"><div class="dark">%s</div>'
                     '<div class="light">%s</div><span>%s</span></div>'
                     % (svg_for(items, 72), svg_for(items, 72), name))
    html = """<!doctype html><meta charset="utf-8">
<style>
body{background:#222;margin:0;padding:24px;font:12px sans-serif;color:#aaa;display:flex;flex-wrap:wrap;gap:18px}
.cell{text-align:center}
.dark{background:#141a24;padding:8px;border-radius:12px;margin-bottom:6px}
.light{background:#f2f4f7;padding:8px;border-radius:12px;margin-bottom:6px}
span{color:#888}
</style>""" + "".join(cells)
    with open(PREVIEW, "w", encoding="utf-8", newline="\n") as f:
        f.write(html)
    print("preview ->", PREVIEW)


if __name__ == "__main__":
    main()
