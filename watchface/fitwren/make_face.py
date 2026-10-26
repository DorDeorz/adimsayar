"""Builds the FitWren watch face for the Xiaomi Smart Band 8 (192 x 490) as a Mi Create project.

Run: python3 make_face.py  (needs Pillow). It writes images/*.png, fitwren.fprj and preview.png.
Compile on Windows with Mi Create's compiler:
    compile.exe -b fitwren.fprj output fitwren.face 1461256429
The bird is the same drawing as ui/BirdPainter.kt (grown bird, happy).
"""
import math
import os
from xml.sax.saxutils import quoteattr

from PIL import Image, ImageDraw, ImageFont

W, H = 192, 490
SS = 4  # supersampling for smooth edges
HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = os.path.join(HERE, "images")
FONT = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

BODY = (0xFF, 0xC9, 0x3C)
BELLY = (0xFF, 0xE8, 0xA3)
WING = (0xF4, 0xA9, 0x22)
BEAK = (0xFF, 0x8A, 0x3D)
EYE = (0x3B, 0x2A, 0x20)
BLUSH = (0xFF, 0x6F, 0x6F, 0x66)
GOLD = (0xFF, 0xB3, 0x00)
WHITE = (0xFF, 0xFF, 0xFF)
GREY = (0x9E, 0x9E, 0x9E)
TRACK = (0x2A, 0x2A, 0x2A)
HEART = (0xEF, 0x53, 0x50)
GREEN = (0x66, 0xBB, 0x6A)

# Data sources of the Band 8 (Mi Create sources.json, xiaomi_band_8).
SRC_HOUR = "0811"
SRC_MINUTE = "1011"
SRC_DAY = "1812"
SRC_MONTH = "1012"
SRC_STEPS = "0821"
SRC_STEP_PERCENT = "1021"
SRC_HEART = "0822"
SRC_BATTERY = "0841"


def canvas(w, h):
    return Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))


def finish(img, w, h):
    return img.resize((w, h), Image.LANCZOS)


def save(img, name):
    img.save(os.path.join(IMAGES, name))
    return name


def digits(prefix, height, color):
    """Ten digit images of the same width; returns names and the size."""
    font = ImageFont.truetype(FONT, int(height * 1.32) * SS)
    width = max(font.getbbox(str(d))[2] - font.getbbox(str(d))[0] for d in range(10)) // SS + 2
    names = []
    for d in range(10):
        img = canvas(width, height)
        draw = ImageDraw.Draw(img)
        box = font.getbbox(str(d))
        x = (width * SS - (box[2] - box[0])) / 2 - box[0]
        y = (height * SS - (box[3] - box[1])) / 2 - box[1]
        draw.text((x, y), str(d), font=font, fill=color)
        names.append(save(finish(img, width, height), f"{prefix}_{d}.png"))
    return names, width, height


def glyph(name, text, height, color):
    font = ImageFont.truetype(FONT, int(height * 1.32) * SS)
    box = font.getbbox(text)
    width = (box[2] - box[0]) // SS + 2
    img = canvas(width, height)
    ImageDraw.Draw(img).text(((width * SS - (box[2] - box[0])) / 2 - box[0], (height * SS - (box[3] - box[1])) / 2 - box[1]), text, font=font, fill=color)
    save(finish(img, width, height), name)
    return name, width, height


def ellipse(draw, cx, cy, rx, ry, color, s, ox, oy):
    draw.ellipse([(ox + (cx - rx) * s), (oy + (cy - ry) * s), (ox + (cx + rx) * s), (oy + (cy + ry) * s)], fill=color)


def bird(size):
    """The grown, happy bird of BirdPainter (unit square 100 x 100)."""
    img = canvas(size, size)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    u = size * SS / 100.0
    scale = 0.94
    ox, oy = 50 * u, 93 * u
    s = u * scale

    def pt(x, y):
        return (ox + x * s, oy + y * s)

    draw.ellipse([26 * u, 90 * u, 74 * u, 98 * u], fill=(0, 0, 0, 0x22))
    # tail
    draw.polygon([pt(20, -30), pt(44, -46), pt(41.2, -26)], fill=WING)
    # legs and feet
    for line in ((-8, -12, -8, 0), (8, -12, 8, 0), (-12, 0, -4, 0), (4, 0, 12, 0)):
        draw.line([pt(line[0], line[1]), pt(line[2], line[3])], fill=BEAK, width=max(1, int(3 * s)))
    ellipse(draw, 0, -38, 28, 28, BODY, s, ox, oy)
    draw.ellipse([*pt(-16, -46), *pt(18, -14)], fill=BELLY)
    for x, angle in ((-24, 15), (24, -15)):
        wing = Image.new("RGBA", layer.size, (0, 0, 0, 0))
        ImageDraw.Draw(wing).ellipse([*pt(x - 7, -52), *pt(x + 7, -26)], fill=WING)
        wing = wing.rotate(-angle, center=pt(x, -38), resample=Image.BICUBIC)
        layer.alpha_composite(wing)
    draw = ImageDraw.Draw(layer)
    # crest
    for box in ((-4, -76, 4, -62), (-11, -71, -3, -61), (3, -71, 11, -61)):
        draw.ellipse([*pt(box[0], box[1]), *pt(box[2], box[3])], fill=WING)
    # eyes
    for x in (-9, 9):
        ellipse(draw, x, -46, 3.6, 3.6, EYE, s, ox, oy)
        ellipse(draw, x + 1.2, -47.2, 1.2, 1.2, WHITE, s, ox, oy)
    # open beak
    draw.polygon([pt(-5, -41), pt(5, -41), pt(0, -36)], fill=BEAK)
    draw.polygon([pt(-4, -35), pt(4, -35), pt(0, -30)], fill=BEAK)
    blush = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(blush)
    ellipse(bd, -17, -38, 4.5, 4.5, BLUSH, s, ox, oy)
    ellipse(bd, 17, -38, 4.5, 4.5, BLUSH, s, ox, oy)
    layer.alpha_composite(blush)
    img.alpha_composite(layer)
    return finish(img, size, size)


def ring(size, width, percent):
    img = canvas(size, size)
    draw = ImageDraw.Draw(img)
    pad = width * SS / 2 + SS
    box = [pad, pad, size * SS - pad, size * SS - pad]
    draw.arc(box, 0, 360, fill=TRACK, width=width * SS)
    if percent > 0:
        color = GOLD if percent >= 100 else BODY
        draw.arc(box, -90, -90 + 360 * min(percent, 100) / 100, fill=color, width=width * SS)
        if percent < 100:
            # round end caps
            r = (size * SS - 2 * pad) / 2
            c = size * SS / 2
            for a in (-90, -90 + 360 * percent / 100):
                x = c + r * math.cos(math.radians(a))
                y = c + r * math.sin(math.radians(a))
                draw.ellipse([x - width * SS / 2, y - width * SS / 2, x + width * SS / 2, y + width * SS / 2], fill=color)
    return finish(img, size, size)


def icon_heart(size):
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    k = size * SS
    d.ellipse([0.05 * k, 0.12 * k, 0.53 * k, 0.6 * k], fill=HEART)
    d.ellipse([0.47 * k, 0.12 * k, 0.95 * k, 0.6 * k], fill=HEART)
    d.polygon([(0.08 * k, 0.45 * k), (0.92 * k, 0.45 * k), (0.5 * k, 0.92 * k)], fill=HEART)
    return finish(img, size, size)


def icon_battery(size):
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    k = size * SS
    d.rounded_rectangle([0.04 * k, 0.28 * k, 0.86 * k, 0.72 * k], radius=0.08 * k, outline=GREEN, width=int(0.09 * k))
    d.rectangle([0.88 * k, 0.4 * k, 0.97 * k, 0.6 * k], fill=GREEN)
    d.rectangle([0.17 * k, 0.4 * k, 0.6 * k, 0.6 * k], fill=GREEN)
    return finish(img, size, size)


def icon_steps(size):
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    k = size * SS
    for (x, y) in ((0.3, 0.42), (0.68, 0.62)):
        d.ellipse([(x - 0.15) * k, (y - 0.3) * k, (x + 0.15) * k, (y + 0.12) * k], fill=BODY)
        d.ellipse([(x - 0.11) * k, (y + 0.16) * k, (x + 0.11) * k, (y + 0.34) * k], fill=BODY)
    return finish(img, size, size)


class Widget:
    def __init__(self, attrs):
        self.attrs = attrs

    def xml(self):
        return "    <Widget " + " ".join(f"{k}={quoteattr(str(v))}" for k, v in self.attrs.items()) + " />"


def image(name, x, y, file, w, h):
    return Widget({"Shape": 30, "Name": name, "Bitmap": file, "X": x, "Y": y, "Width": w, "Height": h, "Alpha": 255, "Visible_Src": 0})


def number(name, x, y, files, w, h, count, source, align=1, spacing=0, blanking=0):
    return Widget({
        "Shape": 32, "Name": name, "BitmapList": "|".join(files), "X": x, "Y": y,
        "Width": w * count + spacing * (count - 1), "Height": h, "Alpha": 255, "Visible_Src": 0,
        "Digits": count, "Alignment": align, "Value_Src": source, "Spacing": spacing, "Blanking": blanking,
    })


def image_list(name, x, y, files, w, h, source):
    return Widget({
        "Shape": 31, "Name": name, "BitmapList": "|".join(f"({v}):{f}" for v, f in files), "X": x, "Y": y,
        "Width": w, "Height": h, "Alpha": 255, "Alignment": 0, "DefaultIndex": 0, "Value_Src": source,
        "Spacing": 0, "Blanking": 0, "Visible_Src": 0,
    })


def main():
    os.makedirs(IMAGES, exist_ok=True)
    widgets = []
    sample = {}

    big_hour, bw, bh = digits("hour", 64, BODY)
    big_min, _, _ = digits("minute", 64, WHITE)
    mid, mw, mh = digits("mid", 26, WHITE)
    small, sw, sh = digits("small", 18, GREY)

    hx = (W - 2 * bw) // 2
    widgets.append(number("hour", hx, 28, big_hour, bw, bh, 2, SRC_HOUR))
    widgets.append(number("minute", hx, 98, big_min, bw, bh, 2, SRC_MINUTE))
    sample["hour"] = (hx, 28, big_hour, bw, "10")
    sample["minute"] = (hx, 98, big_min, bw, "08")

    ring_size, ring_w = 156, 10
    rings = []
    for p in range(0, 101, 10):
        rings.append((p, save(ring(ring_size, ring_w, p), f"ring_{p}.png")))
    ry = 172
    widgets.append(image_list("goal", (W - ring_size) // 2, ry, rings, ring_size, ring_size, SRC_STEP_PERCENT))
    bird_size = 104
    save(bird(bird_size), "bird.png")
    widgets.append(image("bird", (W - bird_size) // 2, ry + (ring_size - bird_size) // 2 - 4, "bird.png", bird_size, bird_size))

    step_icon = 22
    save(icon_steps(step_icon), "icon_steps.png")
    widgets.append(image("steps_icon", (W - step_icon) // 2, 338, "icon_steps.png", step_icon, step_icon))
    steps_w = mw * 5
    widgets.append(number("steps", (W - steps_w) // 2, 364, mid, mw, mh, 5, SRC_STEPS, align=1, blanking=1))

    dot, dw, dh = glyph("dot.png", ".", sh, GREY)
    date_w = sw * 2 + dw + sw * 2
    dx = (W - date_w) // 2
    widgets.append(number("day", dx, 402, small, sw, sh, 2, SRC_DAY))
    widgets.append(image("date_dot", dx + 2 * sw, 402, dot, dw, dh))
    widgets.append(number("month", dx + 2 * sw + dw, 402, small, sw, sh, 2, SRC_MONTH))

    icon = 18
    save(icon_heart(icon), "icon_heart.png")
    save(icon_battery(icon), "icon_battery.png")
    widgets.append(image("heart_icon", 30, 438, "icon_heart.png", icon, icon))
    widgets.append(number("heart", 30 + icon + 3, 438, small, sw, sh, 3, SRC_HEART, align=0, blanking=1))
    widgets.append(image("battery_icon", 104, 438, "icon_battery.png", icon, icon))
    widgets.append(number("battery", 104 + icon + 3, 438, small, sw, sh, 3, SRC_BATTERY, align=0, blanking=1))

    # Preview with sample values (also the store-style thumbnail).
    preview = Image.new("RGBA", (W, H), (0, 0, 0, 255))

    def paste(name, x, y):
        preview.alpha_composite(Image.open(os.path.join(IMAGES, name)).convert("RGBA"), (x, y))

    def paste_number(files, x, y, w, text):
        for i, ch in enumerate(text):
            paste(files[int(ch)], x + i * w, y)

    paste_number(big_hour, hx, 28, bw, "10")
    paste_number(big_min, hx, 98, bw, "08")
    paste("ring_80.png", (W - ring_size) // 2, ry)
    paste("bird.png", (W - bird_size) // 2, ry + (ring_size - bird_size) // 2 - 4)
    paste("icon_steps.png", (W - step_icon) // 2, 338)
    paste_number(mid, (W - mw * 4) // 2, 364, mw, "8421")
    paste_number(small, dx, 402, sw, "26")
    paste(dot, dx + 2 * sw, 402)
    paste_number(small, dx + 2 * sw + dw, 402, sw, "10")
    paste("icon_heart.png", 30, 438)
    paste_number(small, 30 + icon + 3, 438, sw, "72")
    paste("icon_battery.png", 104, 438)
    paste_number(small, 104 + icon + 3, 438, sw, "85")
    preview.save(os.path.join(HERE, "preview.png"))
    thumb = preview.resize((W, H))
    save(thumb, "preview.png")

    with open(os.path.join(HERE, "fitwren.fprj"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('<FaceProject DeviceType="9" Id="">\n')
        f.write('  <Screen Title="FitWren" Bitmap="preview.png">\n')
        for w in widgets:
            f.write(w.xml() + "\n")
        f.write("  </Screen>\n</FaceProject>\n")


if __name__ == "__main__":
    main()
