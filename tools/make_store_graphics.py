#!/usr/bin/env python3
"""Builds the Play Store graphics from real phone screenshots, in the look of
the app: the dark panel, printed legends in Barlow, one amber line.

  tools/shots/raw/*.png             screenshots from the phone (1080x2400)
  tools/store/screen-*.png          phone screenshots with a caption, 1080x2160
  tools/store/feature-1024x500.png  the feature graphic

Play wants phone screenshots at most twice as tall as wide, so the status and
navigation bars are cut off and the screen sits under its caption, at more
than 80 % of the width, without a device frame. Rendered with headless
Chromium so the fonts are the app's own. Run: python3 tools/make_store_graphics.py
"""
import os
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "tools/shots/raw")
STORE = os.path.join(ROOT, "tools/store")
FONTS = os.path.join(ROOT, "app/src/main/res/font")

# order is the order in the store; the first two are what most people see
SCREENS = [
    ("01-steps.png", "Every syllable on a step of the beat"),
    ("02-words.png", "Type your lyrics, pick a flow"),
    ("04-effect.png", "Vocoders, talkboxes and delays on the beat"),
    ("03-voice.png", "44 robot voices, from talking computers to 8-bit chips"),
    ("05-out.png", "Save a WAV that loops, or send it to Ableton Live"),
    ("07-songs.png", "Your songs stay on your phone"),
    ("06-intro.png", "Free, without an account or ads"),
]

STYLE = f"""
@font-face {{ font-family: Legend; src: url(file://{FONTS}/barlow_semicondensed_bold.ttf); font-weight: 700; }}
@font-face {{ font-family: Legend; src: url(file://{FONTS}/barlow_semicondensed_semibold.ttf); font-weight: 600; }}
@font-face {{ font-family: Body; src: url(file://{FONTS}/barlow_regular.ttf); }}
html, body {{ margin: 0; background: #131312; overflow: hidden; }}
.legend {{ font-family: Legend; color: #EAE4D4; }}
.rule {{ background: #FFB000; height: 6px; width: 120px; }}
"""


def render(html, width, height, out):
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(html)
        page = f.name
    subprocess.run(["chromium", "--headless", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    f"--window-size={width},{height}", "--force-device-scale-factor=1", f"--screenshot={out}",
                    f"file://{page}"], check=True, capture_output=True)
    os.remove(page)


def screen(raw, caption, out):
    # the phone's 1080x2400 minus the status bar (132 px) and the navigation bar (126 px)
    html = f"""<html><head><style>{STYLE}
    .cap {{ position: absolute; left: 72px; right: 72px; top: 96px; font-size: 74px; font-weight: 700; line-height: 1.08; letter-spacing: 0.5px; }}
    .rule {{ position: absolute; left: 72px; top: 284px; }}
    .shot {{ position: absolute; top: 352px; left: 50%; transform: translateX(-50%); width: 868px; height: 1722px;
             overflow: hidden; border: 2px solid #3A3934; }}
    .shot img {{ width: 868px; margin-top: -106px; }}
    </style></head><body>
    <div class="legend cap">{caption}</div><div class="rule"></div>
    <div class="shot"><img src="file://{raw}"></div></body></html>"""
    render(html, 1080, 2160, out)


def feature(raw, out):
    html = f"""<html><head><style>{STYLE}
    .kicker {{ position: absolute; left: 56px; top: 92px; font-size: 30px; font-weight: 600; letter-spacing: 3px; color: #948F82; }}
    .title {{ position: absolute; left: 52px; top: 128px; font-size: 104px; font-weight: 700; line-height: 1; }}
    .rule {{ position: absolute; left: 56px; top: 252px; }}
    .line {{ position: absolute; left: 56px; top: 290px; width: 470px; font-family: Body; font-size: 30px; line-height: 1.3; color: #B8B2A4; }}
    .keys {{ position: absolute; right: 44px; top: 70px; width: 420px; height: 338px; overflow: hidden; border: 2px solid #3A3934; }}
    .keys img {{ width: 454px; margin-left: -17px; margin-top: -574px; }}
    </style></head><body>
    <div class="legend kicker">LLOYD'S</div><div class="legend title">ROBOT<br>VOCAL</div>
    <div class="rule" style="top: 360px"></div>
    <div class="line" style="top: 388px">Type lyrics. A robot raps them on the beat.</div>
    <div class="keys"><img src="file://{raw}"></div></body></html>"""
    render(html, 1024, 500, out)


def main():
    os.makedirs(STORE, exist_ok=True)
    for i, (name, caption) in enumerate(SCREENS, start=1):
        screen(os.path.join(RAW, name), caption, os.path.join(STORE, f"screen-{i:02d}.png"))
    feature(os.path.join(RAW, "01-steps.png"), os.path.join(STORE, "feature-1024x500.png"))
    print(f"{len(SCREENS)} screenshots and the feature graphic in tools/store")


if __name__ == "__main__":
    main()
