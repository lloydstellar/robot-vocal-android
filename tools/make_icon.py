#!/usr/bin/env python3
"""Draws the app icon of Robot Vocal from geometry: a face made of the app's
own parts. Two lamps for eyes, and a mouth of eight vocoder band levels in the
four colours of the step keys, on the dark panel with a speaker grille.

Writes the adaptive icon layers (vector drawables), the 512 px store icon and
a sheet with the drafts on dark and light wallpaper:

    python3 tools/make_icon.py

Needs rsvg-convert (librsvg) for the PNGs.
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res/drawable")
STORE = os.path.join(ROOT, "tools/store")
SHOTS = os.path.join(ROOT, "tools/shots")

PANEL = "#1B1B19"
GRILLE = "#2E2D29"
LEGEND = "#EAE4D4"
AMBER = "#FFB000"
STEPS = ["#C9482D", "#DC7E2B", "#DCB53A", "#DCD4C1"]

# the face sits in the 66 dp safe zone of the 108 dp adaptive canvas (21 to 87)
EYES = [(40.5, 44.0), (67.5, 44.0)]
EYE_R = 5.2
BARS = [9, 15, 21, 16, 22, 17, 12, 8]  # band levels: a vowel with two formants
BAR_W, BAR_GAP, BAR_FLOOR = 4.8, 2.0, 79.0


def grille():
    """Speaker grille dots, all over the background layer."""
    dots = []
    for row in range(16):
        for col in range(16):
            x, y = 7 + col * 6.3 + (3.15 if row % 2 else 0), 7 + row * 6.3
            dots.append((x, y))
    return dots


def circle_path(x, y, r):
    return f"M{x - r:.2f},{y:.2f}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z"


def bars():
    total = len(BARS) * BAR_W + (len(BARS) - 1) * BAR_GAP
    x0 = 54 - total / 2
    for i, h in enumerate(BARS):
        x = x0 + i * (BAR_W + BAR_GAP)
        yield x, BAR_FLOOR - h, BAR_W, h, STEPS[i // 2]


def face_shapes():
    shapes = [(circle_path(x, y, EYE_R), AMBER) for x, y in EYES]
    for x, y, w, h, colour in bars():
        shapes.append((f"M{x:.2f},{y:.2f}h{w}v{h}h{-w}z", colour))
    return shapes


def vector(paths, comment):
    body = "\n".join(f'    <path android:fillColor="{c}" android:pathData="{d}" />' for d, c in paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Made by tools/make_icon.py. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f"{body}\n</vector>\n")


def svg(size, layers, background=PANEL, radius=0):
    """An SVG of the 108 unit canvas at [size] px; [layers] are (path, colour) pairs."""
    paths = "\n".join(f'<path fill="{c}" d="{d}"/>' for d, c in layers)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">'
            f'<rect width="108" height="108" rx="{radius}" fill="{background}"/>{paths}</svg>')


def draft_vfd():
    """Draft B: one 14-segment letter R, lit teal in a dark display window."""
    glass = ("M30,24h48v60h-48z", "#060F0D")
    seg = "#66F2D3"
    ghost = "#10261F"
    segs_all = ["M38,30h32v4h-32z", "M36,32h4v20h-4z", "M68,32h4v20h-4z", "M38,52h14v4h-14z", "M56,52h14v4h-14z",
                "M36,56h4v20h-4z", "M68,56h4v20h-4z", "M38,76h32v4h-32z", "M52,34h4v18h-4z", "M52,56h4v18h-4z",
                "M42,36l9,14l-3,2l-9,-14z", "M66,36l-9,14l3,2l9,-14z", "M42,74l9,-14l-3,-2l-9,14z", "M66,74l-9,-14l3,-2l9,14z"]
    lit = [0, 1, 2, 3, 4, 5, 13]
    return [glass] + [(d, seg if i in lit else ghost) for i, d in enumerate(segs_all)]


def draft_keys():
    """Draft C: two lamps over four step keys."""
    shapes = [(circle_path(x, 42, 5), AMBER) for x in (40.5, 67.5)]
    for i, colour in enumerate(STEPS):
        x = 27 + i * 14
        shapes.append((f"M{x},{58}h12v16h-12z", colour))
    return shapes


def main():
    os.makedirs(STORE, exist_ok=True)
    os.makedirs(SHOTS, exist_ok=True)
    dots = [(circle_path(x, y, 1.15), GRILLE) for x, y in grille()]
    background = [("M0,0h108v108h-108z", PANEL)] + dots
    with open(os.path.join(RES, "ic_launcher_background.xml"), "w") as f:
        f.write(vector(background, "Panel with a speaker grille."))
    with open(os.path.join(RES, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(face_shapes(), "Two lamps and eight band levels."))
    with open(os.path.join(RES, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector([(d, "#FFFFFFFF") for d, _ in face_shapes()], "Themed icon."))

    store_svg = os.path.join(STORE, "icon.svg")
    with open(store_svg, "w") as f:
        f.write(svg(512, dots + face_shapes()))
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", store_svg, "-o", os.path.join(STORE, "icon-512.png")], check=True)

    # the drafts at launcher size, masked round as most launchers do, on dark and light wallpaper
    tmp = []
    for name, shapes in [("a", dots + face_shapes()), ("b", draft_vfd()), ("c", dots + draft_keys())]:
        for wall in ["#202124", "#E8EAED"]:
            path = os.path.join(SHOTS, f"icon-draft-{name}-{wall[1:]}.svg")
            with open(path, "w") as f:
                f.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="0 0 240 240">'
                        f'<rect width="240" height="240" fill="{wall}"/><clipPath id="m"><circle cx="120" cy="120" r="96"/></clipPath>'
                        f'<g clip-path="url(#m)"><g transform="translate(12,12) scale(2)">'
                        f'<rect width="108" height="108" fill="{PANEL}"/>'
                        + "".join(f'<path fill="{c}" d="{d}"/>' for d, c in shapes) + "</g></g></svg>")
            png = path[:-4] + ".png"
            subprocess.run(["rsvg-convert", path, "-o", png], check=True)
            os.remove(path)
            tmp.append(png)
    subprocess.run(["magick", "montage", *tmp, "-tile", "2x3", "-geometry", "+8+8", "-background", "#111111",
                    os.path.join(SHOTS, "icon-drafts.png")], check=True)
    for p in tmp:
        os.remove(p)
    print("icon layers, tools/store/icon-512.png and tools/shots/icon-drafts.png written")


if __name__ == "__main__":
    main()
