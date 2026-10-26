r"""Builds FitWren's watch faces for the Xiaomi Smart Band 8 (192 x 490) as Mi Create projects.

Run: python3 make_faces.py  (needs Pillow). Each face gets its own folder <name>/ with
<name>.fprj, images/*.png and preview.png. Compile each on Windows with Mi Create's compiler:
    compile.exe -b <name>\<name>.fprj <name>\output <name>.face 1461256429
The bird is the same drawing as ui/BirdPainter.kt.
"""
import math
import os
from xml.sax.saxutils import quoteattr

from PIL import Image, ImageDraw, ImageFont

W, H = 192, 490
SS = 4  # supersampling for smooth edges
HERE = os.path.dirname(os.path.abspath(__file__))
IMAGES = HERE  # set per face by Face()
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
SRC_WEEKDAY = "2012"
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
    line = font.getbbox("0")  # align on the digits' baseline and height
    width = (box[2] - box[0]) // SS + 2
    img = canvas(width, height)
    ImageDraw.Draw(img).text(((width * SS - (box[2] - box[0])) / 2 - box[0], (height * SS - (line[3] - line[1])) / 2 - line[1]), text, font=font, fill=color)
    save(finish(img, width, height), name)
    return name, width, height


def ellipse(draw, cx, cy, rx, ry, color, s, ox, oy):
    draw.ellipse([(ox + (cx - rx) * s), (oy + (cy - ry) * s), (ox + (cx + rx) * s), (oy + (cy + ry) * s)], fill=color)


def bird(size, sleepy=False):
    """The grown bird of BirdPainter (unit square 100 x 100), happy or sleepy."""
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
        if sleepy:
            draw.arc([*pt(x - 4, -50), *pt(x + 4, -44)], 0, 180, fill=EYE, width=max(1, int(2.4 * s)))
        else:
            ellipse(draw, x, -46, 3.6, 3.6, EYE, s, ox, oy)
            ellipse(draw, x + 1.2, -47.2, 1.2, 1.2, WHITE, s, ox, oy)
    if sleepy:
        draw.polygon([pt(-5, -41), pt(5, -41), pt(0, -33)], fill=BEAK)
        img.alpha_composite(layer)
        zz = ImageFont.truetype(FONT, int(14 * u))
        d = ImageDraw.Draw(img)
        d.text((72 * u, 22 * u), "z", font=ImageFont.truetype(FONT, int(10 * u)), fill=(0x90, 0xA4, 0xAE))
        d.text((80 * u, 6 * u), "z", font=zz, fill=(0x90, 0xA4, 0xAE))
        return finish(img, size, size)
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


def ring(size, width, percent, color=BODY, done=GOLD, track=TRACK):
    img = canvas(size, size)
    draw = ImageDraw.Draw(img)
    pad = width * SS / 2 + SS
    box = [pad, pad, size * SS - pad, size * SS - pad]
    draw.arc(box, 0, 360, fill=track, width=width * SS)
    if percent > 0:
        color = done if percent >= 100 else color
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


def icon_moon(size, color=(0xB3, 0xC7, 0xFF)):
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    k = size * SS
    d.ellipse([0.08 * k, 0.08 * k, 0.92 * k, 0.92 * k], fill=color)
    d.ellipse([0.32 * k, -0.02 * k, 1.06 * k, 0.72 * k], fill=(0, 0, 0, 0))
    # punch the bite out
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0.32 * k, -0.02 * k, 1.06 * k, 0.72 * k], fill=255)
    img.putalpha(Image.composite(Image.new("L", img.size, 0), img.getchannel("A"), mask))
    return finish(img, size, size)


def background(top, bottom, stars=False):
    img = Image.new("RGBA", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,))
    if stars:
        import random
        rnd = random.Random(8)
        for _ in range(38):
            x, y, r = rnd.randrange(W), rnd.randrange(H - 140), rnd.choice((0.6, 0.8, 1.1))
            a = rnd.randrange(90, 230)
            d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))
    return img


def bar(width, height, percent, color=BODY, done=GOLD, track=TRACK):
    img = canvas(width, height)
    d = ImageDraw.Draw(img)
    r = width * SS / 2
    d.rounded_rectangle([0, 0, width * SS, height * SS], radius=r, fill=track)
    if percent > 0:
        fill_h = max(width * SS, height * SS * min(percent, 100) / 100)
        d.rounded_rectangle([0, height * SS - fill_h, width * SS, height * SS], radius=r, fill=done if percent >= 100 else color)
    return finish(img, width, height)


def hbar(width, height, percent, color=BODY, done=GOLD, track=TRACK):
    return bar(height, width, percent, color, done, track).rotate(-90, expand=True)


WEEKDAYS = ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"]


class Widget:
    def __init__(self, attrs, sample):
        self.attrs = attrs
        self.sample = sample

    def xml(self):
        return "    <Widget " + " ".join(f"{k}={quoteattr(str(v))}" for k, v in self.attrs.items()) + " />"


class Face:
    """One watch face: its widgets, images and a preview drawn with sample values."""

    def __init__(self, name, title):
        global IMAGES
        self.name = name
        self.title = title
        self.dir = os.path.join(HERE, name)
        IMAGES = os.path.join(self.dir, "images")
        if os.path.isdir(IMAGES):
            for f in os.listdir(IMAGES):
                os.remove(os.path.join(IMAGES, f))
        os.makedirs(IMAGES, exist_ok=True)
        self.widgets = []
        self.sizes = {}

    def file(self, img, name):
        save(img, name)
        self.sizes[name] = img.size
        return name

    def image(self, name, x, y, file):
        w, h = Image.open(os.path.join(IMAGES, file)).size
        self.widgets.append(Widget({"Shape": 30, "Name": name, "Bitmap": file, "X": x, "Y": y, "Width": w, "Height": h, "Alpha": 255, "Visible_Src": 0}, None))

    def number(self, name, x, y, digit_set, count, source, sample, align=1, blanking=0):
        files, w, h = digit_set
        self.widgets.append(Widget({
            "Shape": 32, "Name": name, "BitmapList": "|".join(files), "X": x, "Y": y,
            "Width": w * count, "Height": h, "Alpha": 255, "Visible_Src": 0,
            "Digits": count, "Alignment": align, "Value_Src": source, "Spacing": 0, "Blanking": blanking,
        }, ("number", files, w, count, align, sample)))

    def image_list(self, name, x, y, files, source, sample):
        w, h = Image.open(os.path.join(IMAGES, files[0][1])).size
        self.widgets.append(Widget({
            "Shape": 31, "Name": name, "BitmapList": "|".join(f"({v}):{f}" for v, f in files), "X": x, "Y": y,
            "Width": w, "Height": h, "Alpha": 255, "Alignment": 0, "DefaultIndex": 0, "Value_Src": source,
            "Spacing": 0, "Blanking": 0, "Visible_Src": 0,
        }, ("list", files, sample)))

    def date_row(self, y, height, day_color, num_color, gap=6):
        date = digits(f"date", height, num_color)
        day_files = [glyph(f"weekday_{i}.png", n, height, day_color) for i, n in enumerate(WEEKDAYS)]
        wd_w = max(f[1] for f in day_files)
        # pad weekday images to one width so the list lines up
        for f in day_files:
            img = Image.open(os.path.join(IMAGES, f[0]))
            padded = Image.new("RGBA", (wd_w, height), (0, 0, 0, 0))
            padded.alpha_composite(img, (wd_w - img.size[0], 0))
            padded.save(os.path.join(IMAGES, f[0]))
        dot = glyph("dot.png", ".", height, num_color)
        total = wd_w + gap + date[1] * 4 + dot[1]
        x = (W - total) // 2
        self.image_list("weekday", x, y, [(i, f[0]) for i, f in enumerate(day_files)], SRC_WEEKDAY, 1)
        x += wd_w + gap
        self.number("day", x, y, date, 2, SRC_DAY, "26")
        self.image("date_dot", x + 2 * date[1], y, dot[0])
        self.number("month", x + 2 * date[1] + dot[1], y, date, 2, SRC_MONTH, "10")

    def write(self):
        preview = Image.new("RGBA", (W, H), (0, 0, 0, 255))

        def paste(file, x, y):
            preview.alpha_composite(Image.open(os.path.join(IMAGES, file)).convert("RGBA"), (x, y))

        for widget in self.widgets:
            a = widget.attrs
            x, y = int(a["X"]), int(a["Y"])
            if widget.sample is None:
                paste(a["Bitmap"], x, y)
            elif widget.sample[0] == "number":
                _, files, w, count, align, text = widget.sample
                offset = {0: 0, 1: (count - len(text)) * w // 2, 2: (count - len(text)) * w}[align]
                for i, ch in enumerate(text):
                    paste(files[int(ch)], x + offset + i * w, y)
            else:
                _, files, value = widget.sample
                chosen = [f for v, f in files if v <= value][-1]
                paste(chosen, x, y)
        preview.save(os.path.join(self.dir, "preview.png"))
        preview.save(os.path.join(IMAGES, "preview.png"))
        with open(os.path.join(self.dir, f"{self.name}.fprj"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write('<FaceProject DeviceType="9" Id="">\n')
            f.write(f'  <Screen Title={quoteattr(self.title)} Bitmap="preview.png">\n')
            for w in self.widgets:
                f.write(w.xml() + "\n")
            f.write("  </Screen>\n</FaceProject>\n")


def rings(face, size, width, **colors):
    return [(p, face.file(ring(size, width, p, **colors), f"ring_{p}.png")) for p in range(0, 101, 10)]


def health_row(face, y, digit_set):
    icon = 18
    face.file(icon_heart(icon), "icon_heart.png")
    face.file(icon_battery(icon), "icon_battery.png")
    face.image("heart_icon", 30, y, "icon_heart.png")
    face.number("heart", 30 + icon + 3, y, digit_set, 3, SRC_HEART, "72", align=0, blanking=1)
    face.image("battery_icon", 104, y, "icon_battery.png")
    face.number("battery", 104 + icon + 3, y, digit_set, 3, SRC_BATTERY, "85", align=0, blanking=1)


def classic():
    """Big two-line time, date, the bird inside the step goal ring, steps, heart rate and battery."""
    f = Face("fitwren", "FitWren")
    hour = digits("hour", 64, BODY)
    minute = digits("minute", 64, WHITE)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 28, hour, 2, SRC_HOUR, "10")
    f.number("minute", hx, 98, minute, 2, SRC_MINUTE, "08")
    f.date_row(168, 20, BODY, WHITE)
    size = 156
    f.image_list("goal", (W - size) // 2, 196, rings(f, size, 10), SRC_STEP_PERCENT, 80)
    f.file(bird(104), "bird.png")
    f.image("bird", (W - 104) // 2, 196 + (size - 104) // 2 - 4, "bird.png")
    f.file(icon_steps(22), "icon_steps.png")
    f.image("steps_icon", (W - 22) // 2, 360, "icon_steps.png")
    mid = digits("mid", 26, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 386, mid, 5, SRC_STEPS, "8421", blanking=1)
    health_row(f, 438, digits("small", 18, GREY))
    f.write()


def night():
    """Dark blue starry sky, a sleepy bird on the moon side, one-line time and a thin goal ring."""
    f = Face("fitwren_gece", "FitWren Gece")
    sky = (0x9C, 0xB4, 0xFF)
    f.file(background((0x0B, 0x10, 0x2E), (0x02, 0x03, 0x0A), stars=True), "background.png")
    f.image("background", 0, 0, "background.png")
    f.file(icon_moon(30), "moon.png")
    f.image("moon", 140, 26, "moon.png")
    hour = digits("hour", 58, WHITE)
    minute = digits("minute", 58, sky)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 60, hour, 2, SRC_HOUR, "22")
    f.number("minute", hx, 124, minute, 2, SRC_MINUTE, "47")
    f.date_row(192, 18, sky, WHITE)
    size = 140
    f.image_list("goal", (W - size) // 2, 228, rings(f, size, 6, color=sky, done=(0xFF, 0xD5, 0x4F), track=(0x1E, 0x24, 0x44)), SRC_STEP_PERCENT, 60)
    f.file(bird(96, sleepy=True), "bird.png")
    f.image("bird", (W - 96) // 2, 228 + (size - 96) // 2 - 2, "bird.png")
    mid = digits("mid", 24, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 384, mid, 5, SRC_STEPS, "6230", blanking=1)
    health_row(f, 432, digits("small", 18, (0x8E, 0x9A, 0xC4)))
    f.write()


def steps_face():
    """Steps first: a tall goal bar with the bird climbing it, big step count, small time on top."""
    f = Face("fitwren_adim", "FitWren Adım")
    green = (0x7C, 0xD9, 0x6B)
    hour = digits("time", 34, WHITE)
    colon = glyph("colon.png", ":", 34, WHITE)
    tw = hour[1] * 4 + colon[1]
    tx = (W - tw) // 2
    f.number("hour", tx, 34, hour, 2, SRC_HOUR, "07")
    f.image("colon", tx + 2 * hour[1], 34, colon[0])
    f.number("minute", tx + 2 * hour[1] + colon[1], 34, hour, 2, SRC_MINUTE, "35")
    f.date_row(80, 16, green, GREY)
    bar_w, bar_h = 26, 230
    bars = [(p, f.file(bar(bar_w, bar_h, p, color=green), f"bar_{p}.png")) for p in range(0, 101, 10)]
    bx, by = 34, 120
    f.image_list("goal", bx, by, bars, SRC_STEP_PERCENT, 60)
    # the bird sits beside the bar's level: one picture per tenth
    f.file(bird(72), "bird.png")
    birds = []
    for p in range(0, 101, 10):
        img = Image.new("RGBA", (84, bar_h + 36), (0, 0, 0, 0))
        b = Image.open(os.path.join(IMAGES, "bird.png"))
        img.alpha_composite(b, (6, int(bar_h - bar_h * p / 100) - 24 + 4))
        birds.append((p, f.file(img, f"bird_{p}.png")))
    f.image_list("bird", bx + bar_w + 6, by - 30, birds, SRC_STEP_PERCENT, 60)
    big = digits("big", 40, WHITE)
    f.file(icon_steps(24), "icon_steps.png")
    f.image("steps_icon", (W - 24) // 2, 362, "icon_steps.png")
    f.number("steps", (W - big[1] * 5) // 2, 390, big, 5, SRC_STEPS, "5120", blanking=1)
    health_row(f, 446, digits("small", 18, GREY))
    f.write()


def simple():
    """Only what matters: a very big time, the date, a thin goal line and a small bird at the bottom."""
    f = Face("fitwren_sade", "FitWren Sade")
    hour = digits("hour", 86, WHITE)
    minute = digits("minute", 86, BODY)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 54, hour, 2, SRC_HOUR, "12")
    f.number("minute", hx, 150, minute, 2, SRC_MINUTE, "30")
    f.date_row(252, 20, GREY, WHITE)
    lines = [(p, f.file(hbar(132, 6, p), f"line_{p}.png")) for p in range(0, 101, 10)]
    f.image_list("goal", (W - 132) // 2, 300, lines, SRC_STEP_PERCENT, 70)
    small = digits("small", 18, GREY)
    f.number("steps", (W - small[1] * 5) // 2, 316, small, 5, SRC_STEPS, "7000", blanking=1)
    f.file(bird(80), "bird.png")
    f.image("bird", (W - 80) // 2, 372, "bird.png")
    f.write()


if __name__ == "__main__":
    classic()
    night()
    steps_face()
    simple()
