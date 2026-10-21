"""Generates the stroke icons as Android vector drawables (24x24 viewport)."""
import os, sys

def circle(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0"

def rect(x, y, w, h, rx):
    return (f"M{x + rx},{y}h{w - 2 * rx}a{rx},{rx} 0 0 1 {rx},{rx}v{h - 2 * rx}"
            f"a{rx},{rx} 0 0 1 {-rx},{rx}h{-(w - 2 * rx)}a{rx},{rx} 0 0 1 {-rx},{-rx}"
            f"v{-(h - 2 * rx)}a{rx},{rx} 0 0 1 {rx},{-rx}z")

S, F = "stroke", "fill"
ICONS = {
    "ic_home": [(S, "M3,11L12,4L21,11L21,20A1,1 0 0 1 20,21L15,21L15,15L9,15L9,21L4,21A1,1 0 0 1 3,20Z")],
    "ic_music": [(S, "M9,18V5l11,-2v13"), (S, circle(6, 18, 3)), (S, circle(17, 16, 3))],
    "ic_radio": [(S, rect(3, 8, 18, 12, 2)), (S, circle(15.5, 14, 3)), (S, "M7,12h3M7,16h3M6,8l11,-5")],
    "ic_camera": [(S, "M3,8a2,2 0 0 1 2,-2h2l2,-2h6l2,2h2a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2z"), (S, circle(12, 13, 3.5))],
    "ic_bluetooth": [(S, "M7,7l10,10l-5,5V2l5,5L7,17")],
    "ic_apps": [(S, rect(4, 4, 6, 6, 1.5)), (S, rect(14, 4, 6, 6, 1.5)), (S, rect(4, 14, 6, 6, 1.5)), (S, rect(14, 14, 6, 6, 1.5))],
    "ic_settings": [(S, "M4,7h10M18,7h2M4,17h4M12,17h8"), (S, circle(16, 7, 2)), (S, circle(10, 17, 2))],
    "ic_prev": [(S, "M19,5L9,12l10,7V5zM5,5v14")],
    "ic_next": [(S, "M5,5l10,7l-10,7V5zM19,5v14")],
    "ic_play": [(F, "M8,5v14l11,-7z")],
    "ic_pause": [(F, "M7,5h3.5v14H7zM13.5,5H17v14h-3.5z")],
    "ic_sun": [(S, circle(12, 12, 4)), (S, "M12,3v2M12,19v2M3,12h2M19,12h2M5.6,5.6l1.4,1.4M17,17l1.4,1.4M5.6,18.4L7,17M17,7l1.4,-1.4")],
    "ic_cloud": [(S, "M7,18h10a4,4 0 0 0 0.5,-7.97A5.5,5.5 0 0 0 6.6,10.1A4,4 0 0 0 7,18z")],
    "ic_rain": [(S, "M7,15h10a4,4 0 0 0 0.5,-7.97A5.5,5.5 0 0 0 6.6,7.1A4,4 0 0 0 7,15zM8,18l-1,2.5M12,18l-1,2.5M16,18l-1,2.5")],
    "ic_storm": [(S, "M7,15h10a4,4 0 0 0 0.5,-7.97A5.5,5.5 0 0 0 6.6,7.1A4,4 0 0 0 7,15zM12.5,15l-2,3.5h3l-2,3.5")],
    "ic_pin": [(S, "M12,21s-7,-6.2 -7,-11.5a7,7 0 0 1 14,0C19,14.8 12,21 12,21z"), (S, circle(12, 9.5, 2.5))],
    "ic_chevron": [(S, "M9,6l6,6l-6,6")],
    "ic_back": [(S, "M15,6l-6,6l6,6")],
    "ic_search": [(S, circle(11, 11, 7)), (S, "M20,20l-3.5,-3.5")],
    "ic_check": [(S, "M5,12.5l4.5,4.5L19,7.5")],
    "ic_image": [(S, rect(3, 5, 18, 14, 2)), (S, circle(9, 10, 1.8)), (S, "M21,16l-5,-5l-8,8")],
    "ic_plus": [(S, "M12,5v14M5,12h14")],
    "ic_minus": [(S, "M5,12h14")],
    "ic_phone": [(S, rect(7, 2.5, 10, 19, 2.5)), (S, "M11,18.5h2")],
    "ic_volume": [(S, "M4,9h4l5,-4v14l-5,-4H4z"), (S, "M16.5,8.5a5,5 0 0 1 0,7M19,6a8.5,8.5 0 0 1 0,12")],
    "ic_vol_up": [(S, "M3,9h4l5,-4v14l-5,-4H3z"), (S, "M15.5,12h6M18.5,9v6")],
    "ic_vol_down": [(S, "M3,9h4l5,-4v14l-5,-4H3z"), (S, "M15.5,12h6")],
    "ic_mute": [(S, "M3,9h4l5,-4v14l-5,-4H3z"), (S, "M16,9.5l5,5M21,9.5l-5,5")],
    "ic_power": [(S, "M12,3v8"), (S, "M7.05,6.05a7,7 0 1,0 9.9,0")],
    "ic_moon": [(S, "M20,14.5A8,8 0 1,1 9.5,4a6.5,6.5 0 0 0 10.5,10.5z")],
    "ic_close": [(S, "M6,6l12,12M18,6L6,18")],
}

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for name, parts in ICONS.items():
    paths = []
    for kind, d in parts:
        if kind == S:
            paths.append(f'    <path android:pathData="{d}" android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" '
                         f'android:strokeWidth="1.7" android:strokeLineCap="round" android:strokeLineJoin="round" />')
        else:
            paths.append(f'    <path android:pathData="{d}" android:fillColor="#FFFFFFFF" />')
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
           + "\n".join(paths) + "\n</vector>\n")
    with open(os.path.join(out, name + ".xml"), "w") as f:
        f.write(xml)
print(len(ICONS), "icons")
