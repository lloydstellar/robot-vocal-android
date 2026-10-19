#!/usr/bin/env python3
"""Draws the app icon of Robot Vocal from geometry: an electro robot in profile,
head tilted up, singing a beam of sound out of the frame. Flat shapes on the
red of the first step key, the way constructivist posters and early electro
sleeves drew their machines.

Writes the adaptive icon layers (vector drawables), the 512 px store icon and
a sheet with the icon in the masks of the launchers, on dark and light
wallpaper, and the themed version:

    python3 tools/make_icon.py

Needs rsvg-convert (librsvg) and ImageMagick for the PNGs.
"""
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res/drawable")
STORE = os.path.join(ROOT, "tools/store")
SHOTS = os.path.join(ROOT, "tools/shots")

RED = "#C9482D"
PANEL = "#1B1B19"
CREAM = "#DCD4C1"
AMBER = "#FFB000"

# The robot is drawn upright and then posed: tilted back 10 degrees and scaled
# into the 66 dp safe zone of the 108 dp canvas, a little left of the centre so
# the beam has room. (rotation, scale, pivot x, pivot y, shift x, shift y)
POSE = (-10, 0.8, 52, 54, -4, 0.5)

VISOR = (37, 44.5, 48.5)  # top, bottom and left end of the visor slot
SLOTS = [57.5, 61, 64.5]  # the mouth grille, cut into the front of the jaw
SLOT_H = 2.2
SLOT_X = 60.1


def rounded(x, y, w, h, r, clockwise=True):
    """A rounded rectangle; clockwise for a shape, counter-clockwise for a hole."""
    if clockwise:
        return (f"M{x + r:g},{y:g}H{x + w - r:g}A{r:g},{r:g} 0 0,1 {x + w:g},{y + r:g}V{y + h - r:g}"
                f"A{r:g},{r:g} 0 0,1 {x + w - r:g},{y + h:g}H{x + r:g}A{r:g},{r:g} 0 0,1 {x:g},{y + h - r:g}"
                f"V{y + r:g}A{r:g},{r:g} 0 0,1 {x + r:g},{y:g}Z")
    return (f"M{x + r:g},{y:g}A{r:g},{r:g} 0 0,0 {x:g},{y + r:g}V{y + h - r:g}A{r:g},{r:g} 0 0,0 {x + r:g},{y + h:g}"
            f"H{x + w - r:g}A{r:g},{r:g} 0 0,0 {x + w:g},{y + h - r:g}V{y + r:g}A{r:g},{r:g} 0 0,0 {x + w - r:g},{y:g}Z")


def hole(cx, cy, r):
    """A round hole: a circle drawn counter-clockwise."""
    return f"M{cx + r:g},{cy:g}A{r:g},{r:g} 0 1,0 {cx - r:g},{cy:g}A{r:g},{r:g} 0 1,0 {cx + r:g},{cy:g}Z"


def head():
    """The head as one path filled non-zero: its parts clockwise, the holes
    through it counter-clockwise, so the background shows through them and the
    themed icon keeps them."""
    top, bottom, left = VISOR
    r = (bottom - top) / 2
    skull = f"M41,25H66Q70,25 70,29V{top:g}H{left + r:g}A{r:g},{r:g} 0 0,0 {left + r:g},{bottom:g}H70V56H31V35Z"
    jaw = "M36,54H71Q74,54 74,57"
    for y in SLOTS:
        jaw += f"V{y:g}H{SLOT_X:g}A{SLOT_H / 2:g},{SLOT_H / 2:g} 0 0,0 {SLOT_X:g},{y + SLOT_H:g}H74"
    jaw += "V67L69,73H40Q36,73 36,69Z"
    vents = "".join(rounded(x, 36, 2.2, 13, 1.1, clockwise=False) for x in (37, 41, 45))
    bolt = hole(43, 63, 2.3)
    neck = rounded(45, 74, 14, 3.4, 1) + rounded(46.5, 78.4, 11, 3.4, 1)
    return skull + jaw + vents + bolt + neck


def visor():
    top, bottom, left = VISOR
    r = (bottom - top) / 2
    return f"M70,{top:g}H{left + r:g}A{r:g},{r:g} 0 0,0 {left + r:g},{bottom:g}H70Z"


def beam():
    """The sound, opening from the mouth to beyond the edge."""
    return "M77.5,62L112,43L112,85Z"


def robot(themed=False):
    """(path, colour) pairs, back to front; the themed icon is one colour without the lit visor."""
    if themed:
        return [(beam(), "#FFFFFF"), (head(), "#FFFFFF")]
    return [(beam(), CREAM), (head(), PANEL), (visor(), AMBER)]


def vector(paths, comment, posed=True):
    rotation, scale, px, py, dx, dy = POSE
    body = "\n".join(f'{"    " * (2 if posed else 1)}<path android:fillColor="{c}" android:pathData="{d}" />'
                     for d, c in paths)
    if posed:
        body = (f'    <group android:pivotX="{px}" android:pivotY="{py}" android:rotation="{rotation}"\n'
                f'        android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{dx}" android:translateY="{dy}">\n'
                f"{body}\n    </group>")
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Made by tools/make_icon.py. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            f"{body}\n</vector>\n")


def svg(size, view="18 18 72 72", mask="", wall=None, themed=False):
    """The icon as an SVG of [size] px showing [view] of the 108 unit canvas, clipped to [mask] if given."""
    rotation, scale, px, py, dx, dy = POSE
    posed = (f'<g transform="translate({dx} {dy}) translate({px} {py}) rotate({rotation}) scale({scale}) '
             f'translate({-px} {-py})">' + "".join(f'<path fill="{c}" d="{d}"/>' for d, c in robot(themed)) + "</g>")
    icon = f'<rect width="108" height="108" fill="{"#3C4043" if themed else RED}"/>' + posed
    if mask:
        icon = f'<clipPath id="m">{mask}</clipPath><g clip-path="url(#m)">{icon}</g>'
    back = f'<rect width="108" height="108" fill="{wall}"/>' if wall else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{back}{icon}</svg>'


def render(svg_text, png):
    path = png[:-4] + ".svg"
    with open(path, "w") as f:
        f.write(svg_text)
    subprocess.run(["rsvg-convert", path, "-o", png], check=True)
    os.remove(path)


def main():
    os.makedirs(STORE, exist_ok=True)
    os.makedirs(SHOTS, exist_ok=True)
    with open(os.path.join(RES, "ic_launcher_background.xml"), "w") as f:
        f.write(vector([("M0,0h108v108h-108z", RED)], "The red of the first step key.", posed=False))
    with open(os.path.join(RES, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(robot(), "A robot in profile singing a beam of sound."))
    with open(os.path.join(RES, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector(robot(themed=True), "Themed icon."))

    # the store icon is the part a launcher shows, full bleed: Google Play rounds the corners itself
    with open(os.path.join(STORE, "icon.svg"), "w") as f:
        f.write(svg(512))
    subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", os.path.join(STORE, "icon.svg"),
                    "-o", os.path.join(STORE, "icon-512.png")], check=True)

    # the masks of the launchers, at 192 and at 48 px, on dark and light wallpaper
    masks = {
        "circle": '<circle cx="54" cy="54" r="36"/>',
        "squircle": '<path d="M54,18C83,18 90,25 90,54C90,83 83,90 54,90C25,90 18,83 18,54C18,25 25,18 54,18Z"/>',
        "rounded": '<rect x="18" y="18" width="72" height="72" rx="12"/>',
    }
    tiles = []
    for wall in ["#202124", "#E8EAED"]:
        for name, mask in masks.items():
            tiles.append(os.path.join(SHOTS, f"icon-{name}-{wall[1:]}.png"))
            render(svg(192, "12 12 84 84", mask, wall), tiles[-1])
        tiles.append(os.path.join(SHOTS, f"icon-themed-{wall[1:]}.png"))
        render(svg(192, "12 12 84 84", masks["circle"], wall, themed=True), tiles[-1])
    for wall in ["#202124", "#E8EAED"]:
        tiles.append(os.path.join(SHOTS, f"icon-small-{wall[1:]}.png"))
        render(svg(48, "12 12 84 84", masks["circle"], wall), tiles[-1])
    subprocess.run(["magick", "montage", *tiles, "-tile", "4x3", "-geometry", "192x192+8+8>", "-gravity", "center",
                    "-background", "#111111", os.path.join(SHOTS, "icon-preview.png")], check=True)
    for p in tiles:
        os.remove(p)
    print("icon layers, tools/store/icon-512.png and tools/shots/icon-preview.png written")


if __name__ == "__main__":
    main()
