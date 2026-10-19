#!/usr/bin/env python3
"""Draws the app icon of Robot Vocal: a computer terminal of 1981 on yellow,
seen from above and a little from the side, with a robot on its screen in the
style of the mannequins of the electro of that time: slicked hair, a blank
face, a red shirt and a black tie. The terminal is a small 3D model projected
in perspective and drawn in flat shades, so its angles are true.

Writes the adaptive icon layers (vector drawables), the 512 px store icon and
a sheet with the icon in the masks of the launchers, on dark and light
wallpaper, and the themed version:

    python3 tools/make_icon.py

Needs rsvg-convert (librsvg) and ImageMagick for the PNGs.
"""
import math
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res/drawable")
STORE = os.path.join(ROOT, "tools/store")
SHOTS = os.path.join(ROOT, "tools/shots")

YELLOW = "#EDB92E"
BLACK = "#161614"
SIDE = "#3A3934"
DECK = "#2A2926"
KEY = "#55534C"
GLASS = "#1F2421"
SKIN = "#E3D7C3"
COLLAR = "#EAE4D4"
RED = "#C9482D"

# The terminal: a monitor W wide, H high and D deep with a flat top, its back
# T narrower at the sides and T higher at the bottom, the way a picture tube's
# housing narrows; on a base whose keyboard slopes KH down over KD towards us,
# with a lip of KF. Units are arbitrary; the drawing is fitted to BOX on the
# 108 dp canvas.
W, H, D, T = 1.0, 1.35, 1.5, 0.16
KD, KH, KF = 0.8, 0.16, 0.34
SCREEN_MARGIN = 0.15
YAW, PITCH, DISTANCE = 0.42, -0.36, 7.0
BOX = (26, 28, 82, 82)  # x0, y0, x1, y1: inside the 66 dp safe zone, the corners just touch its edge
SEAM = 0.3  # an outline in the face's own colour, so neighbouring faces meet without a hairline of background between them

MONITOR = [
    ([(-W, 0, 0), (W, 0, 0), (W, H, 0), (-W, H, 0)], BLACK),
    ([(W, 0, 0), (W - T, T, D), (W - T, H, D), (W, H, 0)], SIDE),
    ([(-W, 0, 0), (-W + T, T, D), (-W + T, H, D), (-W, H, 0)], SIDE),
    ([(-W, H, 0), (W, H, 0), (W - T, H, D), (-W + T, H, D)], DECK),
    ([(-W, 0, 0), (W, 0, 0), (W - T, T, D), (-W + T, T, D)], BLACK),
    ([(-W + T, T, D), (W - T, T, D), (W - T, H, D), (-W + T, H, D)], BLACK),
]
BASE = [
    ([(-W, -KH, -KD), (W, -KH, -KD), (W, 0, 0), (-W, 0, 0)], DECK),
    ([(-W, -KF, -KD), (W, -KF, -KD), (W, -KH, -KD), (-W, -KH, -KD)], BLACK),
    ([(W, -KF, -KD), (W, -KF, 0), (W, 0, 0), (W, -KH, -KD)], SIDE),
    ([(-W, -KF, -KD), (-W, -KF, 0), (-W, 0, 0), (-W, -KH, -KD)], SIDE),
    ([(-W, -KF, 0), (W, -KF, 0), (W, 0, 0), (-W, 0, 0)], BLACK),
    ([(-W, -KF, -KD), (W, -KF, -KD), (W, -KF, 0), (-W, -KF, 0)], BLACK),
]

# The robot on the screen, in the screen's own coordinates: u from left to right, v from bottom to top, 0 to 1.
SHIRT = [(0.1, 0.0), (0.9, 0.0), (0.8, 0.2), (0.2, 0.2)]
NECK = [(0.4, 0.2), (0.6, 0.2), (0.56, 0.27), (0.44, 0.27)]
COLLARS = [[(0.38, 0.2), (0.5, 0.13), (0.5, 0.2)], [(0.5, 0.13), (0.62, 0.2), (0.5, 0.2)]]
TIE = [(0.47, 0.0), (0.53, 0.0), (0.535, 0.16), (0.465, 0.16)]
HEAD = [(0.31, 0.27), (0.69, 0.27), (0.71, 0.74), (0.29, 0.74)]
HAIR = [(0.28, 0.68), (0.72, 0.68), (0.72, 0.86), (0.62, 0.92), (0.3, 0.9)]
EYES = [[(0.36, 0.55), (0.46, 0.55), (0.46, 0.58), (0.36, 0.58)], [(0.54, 0.55), (0.64, 0.55), (0.64, 0.58), (0.54, 0.58)]]
MOUTH = [(0.43, 0.37), (0.57, 0.37), (0.57, 0.395), (0.43, 0.395)]


class Camera:
    """Turns the model by YAW, looks down on it by PITCH, and projects it in perspective."""

    def __init__(self, scale=1.0, cx=0.0, cy=0.0):
        self.scale, self.cx, self.cy = scale, cx, cy

    @staticmethod
    def view(p):
        x, y, z = p
        c, s = math.cos(YAW), math.sin(YAW)
        x, z = c * x + s * z, -s * x + c * z
        c, s = math.cos(PITCH), math.sin(PITCH)
        return (x, c * y - s * z, s * y + c * z)

    def project(self, p):
        x, y, z = self.view(p)
        k = DISTANCE / (DISTANCE + z)
        return (self.cx + x * k * self.scale, self.cy - y * k * self.scale)

    def path(self, points):
        return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in map(self.project, points)) + "Z"


def fitted():
    """The camera that puts the whole terminal inside BOX."""
    points = [Camera().project(p) for face, _ in MONITOR + BASE for p in face]
    x0, x1 = min(p[0] for p in points), max(p[0] for p in points)
    y0, y1 = min(p[1] for p in points), max(p[1] for p in points)
    s = min((BOX[2] - BOX[0]) / (x1 - x0), (BOX[3] - BOX[1]) / (y1 - y0))
    return Camera(s, (BOX[0] + BOX[2]) / 2 - (x0 + x1) / 2 * s, (BOX[1] + BOX[3]) / 2 - (y0 + y1) / 2 * s)


def facing(solid):
    """The faces of a convex solid that face the camera."""
    def sub(a, b):
        return (a[0] - b[0], a[1] - b[1], a[2] - b[2])

    def mean(points):
        return tuple(sum(p[i] for p in points) / len(points) for i in range(3))

    inside = mean([Camera.view(p) for face, _ in solid for p in face])
    out = []
    for face, colour in solid:
        v = [Camera.view(p) for p in face]
        a, b = sub(v[1], v[0]), sub(v[2], v[0])
        n = (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        centre = mean(v)
        outward = sum(n[i] * sub(centre, inside)[i] for i in range(3))
        towards = sum(n[i] * sub((0, 0, -DISTANCE), centre)[i] for i in range(3))
        if outward * towards > 0:
            out.append((face, colour))
    return out


def screen(u, v):
    """A point on the glass, slightly in front of the monitor's face."""
    m = SCREEN_MARGIN
    return (-W + m + (2 * W - 2 * m) * u, m + (H - 2 * m) * v, -0.001)


def keys():
    """Three rows of nine keys on the sloping deck."""
    def deck(u, v):
        return (u, -KH + KH * v, -KD + KD * v)
    for row in range(3):
        for k in range(9):
            u, v = -0.86 + k * 0.195, 0.14 + row * 0.27
            yield [deck(u, v), deck(u + 0.15, v), deck(u + 0.15, v + 0.18), deck(u, v + 0.18)]


def icon():
    """(path, colour) pairs, back to front. The terminal's faces come first; each is outlined in its own colour (see [SEAM])."""
    cam = fitted()
    out = [(cam.path(face), colour) for solid in (MONITOR, BASE) for face, colour in facing(solid)]
    out += [(cam.path(k), KEY) for k in keys()]
    on_screen = lambda points: cam.path([screen(u, v) for u, v in points])
    out.append((on_screen([(0, 0), (1, 0), (1, 1), (0, 1)]), GLASS))
    out += [(on_screen(SHIRT), RED), (on_screen(NECK), SKIN)]
    out += [(on_screen(c), COLLAR) for c in COLLARS]
    out += [(on_screen(TIE), BLACK), (on_screen(HEAD), SKIN), (on_screen(HAIR), BLACK)]
    out += [(on_screen(e), BLACK) for e in EYES] + [(on_screen(MOUTH), BLACK)]
    return out


def faces():
    """How many of [icon]'s paths are faces of the terminal."""
    return sum(len(facing(solid)) for solid in (MONITOR, BASE))


def themed():
    """
    One colour: the terminal with the screen cut out, and the robot in it as a
    silhouette. Its paths are filled even-odd, so the eyes and mouth are holes
    and where the hair overlaps the face a thin line stays open between them.
    """
    cam = fitted()
    on_screen = lambda points: cam.path([screen(u, v) for u, v in points])
    front = cam.path(MONITOR[0][0])
    body = [cam.path(face) for solid in (MONITOR, BASE) for face, _ in facing(solid)]
    glass = on_screen([(0, 0), (1, 0), (1, 1), (0, 1)])
    figure = "".join(on_screen(part) for part in [SHIRT, NECK, HEAD, HAIR, *EYES, MOUTH])
    return [("".join(p for p in body if p != front), "#FFFFFFFF", None),
            (front + glass, "#FFFFFFFF", "evenOdd"),
            (figure, "#FFFFFFFF", "evenOdd")]


def vector(paths, comment, seams=0):
    """A vector drawable of [paths]; the first [seams] of them are outlined in their own colour."""
    lines = []
    for i, item in enumerate(paths):
        d, colour, fill_type = item if len(item) == 3 else (*item, None)
        seamed = i < seams
        rule = f' android:fillType="{fill_type}"' if fill_type else ""
        seam = f' android:strokeColor="{colour}" android:strokeWidth="{SEAM}" android:strokeLineJoin="round"' if seamed else ""
        lines.append(f'    <path android:fillColor="{colour}"{rule}{seam} android:pathData="{d}" />')
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Made by tools/make_icon.py. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + "\n".join(lines) + "\n</vector>\n")


def svg(size, view="18 18 72 72", mask="", wall=None, themed_look=False):
    """The icon as an SVG of [size] px showing [view] of the 108 unit canvas, clipped to [mask] if given."""
    if themed_look:
        shapes = "".join(f'<path fill="#FFFFFF" fill-rule="{"evenodd" if rule else "nonzero"}" d="{d}"/>' for d, _, rule in themed())
        content = f'<rect width="108" height="108" fill="#3C4043"/>{shapes}'
    else:
        n = faces()
        content = f'<rect width="108" height="108" fill="{YELLOW}"/>' + "".join(
            f'<path fill="{c}" d="{d}"' + (f' stroke="{c}" stroke-width="{SEAM}" stroke-linejoin="round"' if i < n else "") + "/>"
            for i, (d, c) in enumerate(icon()))
    if mask:
        content = f'<clipPath id="m">{mask}</clipPath><g clip-path="url(#m)">{content}</g>'
    back = f'<rect width="108" height="108" fill="{wall}"/>' if wall else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="{view}">{back}{content}</svg>'


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
        f.write(vector([("M0,0h108v108h-108z", YELLOW)], "Yellow."))
    with open(os.path.join(RES, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(icon(), "A terminal of 1981 with a robot on its screen.", seams=faces()))
    with open(os.path.join(RES, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector(themed(), "Themed icon."))

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
        render(svg(192, "12 12 84 84", masks["circle"], wall, themed_look=True), tiles[-1])
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
