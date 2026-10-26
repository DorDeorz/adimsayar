r"""Builds FitWren's watch faces for the Xiaomi Smart Band 8 (192 x 490) as Mi Create projects.

Run: python3 make_faces.py  (needs Pillow). Each face gets its own folder <name>/ with
<name>.fprj, images/*.png and preview.png. Compile each on Windows with Mi Create's compiler:
    compile.exe -b <name>\<name>.fprj <name>\output <name>.face 1461256429
The bird is the same drawing as ui/BirdPainter.kt.
"""
import math
import os
from xml.sax.saxutils import quoteattr

from PIL import Image, ImageDraw, ImageFilter, ImageFont

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
SRC_KCAL = "0823"
SRC_KCAL_PERCENT = "1023"


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

    def number(self, name, x, y, digit_set, count, source, sample, align=1, blanking=0, spacing=0):
        files, w, h = digit_set
        self.widgets.append(Widget({
            "Shape": 32, "Name": name, "BitmapList": "|".join(files), "X": x, "Y": y,
            "Width": w * count + spacing * (count - 1), "Height": h, "Alpha": 255, "Visible_Src": 0,
            "Digits": count, "Alignment": align, "Value_Src": source, "Spacing": spacing, "Blanking": blanking,
        }, ("number", files, w + spacing, count, align, sample)))

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
            elif widget.sample[0] == "analog":
                cx, cy = x + int(a["Width"]) // 2, y + int(a["Height"]) // 2
                for file, (px, py), angle in widget.sample[1]:
                    img = Image.open(os.path.join(IMAGES, file)).convert("RGBA")
                    big = Image.new("RGBA", (400, 400), (0, 0, 0, 0))
                    big.alpha_composite(img, (200 - px, 200 - py))
                    big = big.rotate(-angle, center=(200, 200), resample=Image.BICUBIC)
                    preview.alpha_composite(big, (cx - 200, cy - 200)) if cx >= 200 and cy >= 200 else preview.paste(big, (cx - 200, cy - 200), big)
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




def glow_digits(prefix, height, color, blur=4):
    """Digits with a soft neon glow; the image is padded so the glow is not cut."""
    pad = blur * 2
    font = ImageFont.truetype(FONT, int(height * 1.32) * SS)
    width = max(font.getbbox(str(d))[2] - font.getbbox(str(d))[0] for d in range(10)) // SS + 2
    names = []
    for d in range(10):
        img = canvas(width + 2 * pad, height + 2 * pad)
        draw = ImageDraw.Draw(img)
        box = font.getbbox(str(d))
        x = (width * SS - (box[2] - box[0])) / 2 - box[0] + pad * SS
        y = (height * SS - (box[3] - box[1])) / 2 - box[1] + pad * SS
        draw.text((x, y), str(d), font=font, fill=color)
        sharp = finish(img, width + 2 * pad, height + 2 * pad)
        halo = sharp.filter(ImageFilter.GaussianBlur(blur))
        out = Image.new("RGBA", sharp.size, (0, 0, 0, 0))
        out.alpha_composite(halo)
        out.alpha_composite(halo)
        core = sharp.copy()
        whiter = Image.new("RGBA", sharp.size, (255, 255, 255, 0))
        whiter.putalpha(sharp.getchannel("A").point(lambda a: a * 0.45))
        out.alpha_composite(core)
        out.alpha_composite(whiter)
        names.append(save(out, f"{prefix}_{d}.png"))
    return names, width + 2 * pad, height + 2 * pad


def path_points(top, bottom, amplitude=46, turns=2.2):
    """A winding road from the bottom (0 %) to the top (100 %)."""
    pts = []
    for i in range(201):
        t = i / 200
        y = bottom - (bottom - top) * t
        x = W / 2 + amplitude * math.sin(t * turns * 2 * math.pi)
        pts.append((x, y))
    return pts


def road():
    """The day as a road: the bird walks up a winding path as steps add up, a flag waits at the top."""
    f = Face("fitwren_yol", "FitWren Yol")
    sand = (0xE8, 0xC9, 0x8F)
    grass = (0x7C, 0xC5, 0x76)
    hour = digits("time", 36, WHITE)
    colon = glyph("colon.png", ":", 36, BODY)
    tw = hour[1] * 4 + colon[1]
    tx = (W - tw) // 2
    f.number("hour", tx, 30, hour, 2, SRC_HOUR, "16")
    f.image("colon", tx + 2 * hour[1], 30, colon[0])
    f.number("minute", tx + 2 * hour[1] + colon[1], 30, hour, 2, SRC_MINUTE, "20")
    f.date_row(76, 16, grass, GREY)
    top, bottom = 132, 372
    pts = path_points(top, bottom)
    bg = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(bg)
    d.line([(x * SS, y * SS) for x, y in pts], fill=(0x3A, 0x33, 0x28), width=16 * SS, joint="curve")
    d.line([(x * SS, y * SS) for x, y in pts], fill=(0x55, 0x4A, 0x38), width=10 * SS, joint="curve")
    for k in range(0, 201, 8):
        x, y = pts[k]
        d.ellipse([(x - 1.6) * SS, (y - 1.6) * SS, (x + 1.6) * SS, (y + 1.6) * SS], fill=sand)
    fx, fy = pts[-1]
    d.line([(fx * SS, fy * SS), (fx * SS, (fy - 26) * SS)], fill=WHITE, width=2 * SS)
    d.polygon([(fx * SS, (fy - 26) * SS), ((fx + 16) * SS, (fy - 21) * SS), (fx * SS, (fy - 15) * SS)], fill=(0xE5, 0x39, 0x35))
    f.file(finish(bg, W, H), "road.png")
    f.image("road", 0, 0, "road.png")
    f.file(bird(46), "bird.png")
    b = Image.open(os.path.join(IMAGES, "bird.png"))
    walkers = []
    span = (top - 34, bottom + 8)
    for p in range(0, 101, 10):
        img = Image.new("RGBA", (W, span[1] - span[0]), (0, 0, 0, 0))
        x, y = pts[min(200, p * 2)]
        img.alpha_composite(b, (int(x - 23), int(y - 40 - span[0])))
        walkers.append((p, f.file(img, f"walk_{p}.png")))
    f.image_list("walker", 0, span[0], walkers, SRC_STEP_PERCENT, 60)
    mid = digits("mid", 28, WHITE)
    f.file(icon_steps(22), "icon_steps.png")
    f.image("steps_icon", (W - 22) // 2, 388, "icon_steps.png")
    f.number("steps", (W - mid[1] * 5) // 2, 412, mid, 5, SRC_STEPS, "6480", blanking=1)
    health_row(f, 452, digits("small", 16, GREY))
    f.write()


def hand(length, width, color, tail=12, tip=None):
    """A rounded hand pointing up; returns the image and its pivot."""
    w, h = width + 4, length + tail
    img = canvas(w, h)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2 * SS, 0, (width + 2) * SS, h * SS], radius=width * SS / 2, fill=color)
    if tip:
        d.rounded_rectangle([2 * SS, 0, (width + 2) * SS, length * 0.35 * SS], radius=width * SS / 2, fill=tip)
    return finish(img, w, h), (w // 2, length)


def analog():
    """An analog dial with the bird in the middle; the hour ticks double as the step goal."""
    f = Face("fitwren_analog", "FitWren Analog")
    size, cy = 184, 150
    dial = canvas(size, size)
    d = ImageDraw.Draw(dial)
    c = size * SS / 2
    d.ellipse([2 * SS, 2 * SS, (size - 2) * SS, (size - 2) * SS], fill=(0x16, 0x16, 0x16))
    for i in range(60):
        a = math.radians(i * 6 - 90)
        r1 = c - 8 * SS
        r2 = r1 - (12 if i % 5 == 0 else 5) * SS
        d.line([(c + r1 * math.cos(a), c + r1 * math.sin(a)), (c + r2 * math.cos(a), c + r2 * math.sin(a))],
               fill=BODY if i % 15 == 0 else (WHITE if i % 5 == 0 else (0x60, 0x60, 0x60)), width=(3 if i % 5 == 0 else 1) * SS)
    f.file(finish(dial, size, size), "dial.png")
    dx = (W - size) // 2
    f.image("dial", dx, cy - size // 2, "dial.png")
    f.file(bird(70), "bird.png")
    f.image("bird", (W - 70) // 2, cy - 30, "bird.png")
    hour_img, hour_pivot = hand(46, 7, WHITE)
    min_img, min_pivot = hand(70, 5, WHITE, tip=BODY)
    sec_img, sec_pivot = hand(78, 2, (0xE5, 0x39, 0x35), tail=18)
    f.file(hour_img, "hand_hour.png")
    f.file(min_img, "hand_minute.png")
    f.file(sec_img, "hand_second.png")
    f.widgets.append(Widget({
        "Shape": 27, "Name": "clock", "X": dx, "Y": cy - size // 2, "Width": size, "Height": size, "Alpha": 255,
        "Visible_Src": 0, "HourHandCorrection_En": 1, "MinuteHandCorrection_En": 0,
        "Background_ImageName": "", "BgImage_rotate_xc": 0, "BgImage_rotate_yc": 0,
        "HourHand_ImageName": "hand_hour.png", "HourImage_rotate_xc": hour_pivot[0], "HourImage_rotate_yc": hour_pivot[1],
        "MinuteHand_Image": "hand_minute.png", "MinuteImage_rotate_xc": min_pivot[0], "MinuteImage_rotate_yc": min_pivot[1],
        "SecondHand_Image": "hand_second.png", "SecondImage_rotate_xc": sec_pivot[0], "SecondImage_rotate_yc": sec_pivot[1],
    }, ("analog", [("hand_hour.png", hour_pivot, 10 * 30 + 9), ("hand_minute.png", min_pivot, 18 * 6), ("hand_second.png", sec_pivot, 36 * 6)])))
    f.date_row(256, 20, BODY, WHITE)
    size2 = 92
    f.image_list("goal", (W - size2) // 2, 296, rings(f, size2, 7), SRC_STEP_PERCENT, 70)
    f.file(icon_steps(26), "icon_steps.png")
    f.image("steps_icon", (W - 26) // 2, 296 + (size2 - 26) // 2, "icon_steps.png")
    mid = digits("mid", 26, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 400, mid, 5, SRC_STEPS, "7012", blanking=1)
    health_row(f, 444, digits("small", 18, GREY))
    f.write()


def neon():
    """Neon on black: glowing cyan and pink time, a pink step arc and a small bird."""
    f = Face("fitwren_neon", "FitWren Neon")
    cyan, pink = (0x29, 0xE6, 0xFF), (0xFF, 0x4F, 0xC3)
    hour = glow_digits("hour", 62, cyan)
    minute = glow_digits("minute", 62, pink)
    tight = -14
    hx = (W - 2 * hour[1] - tight) // 2
    f.number("hour", hx, 22, hour, 2, SRC_HOUR, "21", spacing=tight)
    f.number("minute", hx, 96, minute, 2, SRC_MINUTE, "15", spacing=tight)
    f.date_row(184, 18, pink, cyan)
    size = 150
    f.image_list("goal", (W - size) // 2, 218, rings(f, size, 6, color=pink, done=cyan, track=(0x24, 0x10, 0x2A)), SRC_STEP_PERCENT, 55)
    f.file(bird(90), "bird.png")
    f.image("bird", (W - 90) // 2, 218 + (size - 90) // 2 - 4, "bird.png")
    mid = glow_digits("mid", 24, cyan, blur=3)
    f.number("steps", (W - mid[1] * 5 + 40) // 2, 374, mid, 5, SRC_STEPS, "5502", blanking=1, spacing=-10)
    health_row(f, 438, digits("small", 18, (0xB0, 0x9C, 0xC8)))
    f.write()


def branch(dark=False):
    """The bird sitting on a branch with leaves: a pastel morning, or the same scene at dusk."""
    if dark:
        f = Face("fitwren_dal_gece", "FitWren Dal Gece")
        ink, accent, done, track, soft = (0xF2, 0xE6, 0xD8), (0xFF, 0xB0, 0x6A), (0x81, 0xC7, 0x84), (0x3A, 0x30, 0x3E), (0xB8, 0xA8, 0x9C)
        sky = background((0x1E, 0x17, 0x2E), (0x07, 0x05, 0x0D), stars=True)
        brown, leaf = (0x6D, 0x52, 0x48), (0x4E, 0x8F, 0x55)
    else:
        f = Face("fitwren_dal", "FitWren Dal")
        ink, accent, done, track, soft = (0x3B, 0x2A, 0x20), (0xE0, 0x7A, 0x2F), (0x43, 0xA0, 0x47), (0xF3, 0xC9, 0xB0), (0x6D, 0x5A, 0x50)
        sky = background((0xFF, 0xF1, 0xD6), (0xFF, 0xD3, 0xC2))
        brown, leaf = (0x8D, 0x6E, 0x63), (0x81, 0xC7, 0x84)
    f.file(sky, "background.png")
    f.image("background", 0, 0, "background.png")
    hour = digits("hour", 70, ink)
    minute = digits("minute", 70, accent)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 30, hour, 2, SRC_HOUR, "09")
    f.number("minute", hx, 108, minute, 2, SRC_MINUTE, "41")
    f.date_row(190, 18, accent, ink)
    art = Image.new("RGBA", (W * SS, 150 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    d.line([(0, 120 * SS), (80 * SS, 112 * SS), (150 * SS, 100 * SS), (W * SS, 92 * SS)], fill=brown, width=8 * SS, joint="curve")
    d.line([(118 * SS, 104 * SS), (150 * SS, 126 * SS)], fill=brown, width=5 * SS)
    for (x, y, a) in ((40, 104, -30), (150, 128, 30), (172, 84, -20), (20, 128, 20)):
        lf = Image.new("RGBA", (30 * SS, 16 * SS), (0, 0, 0, 0))
        ImageDraw.Draw(lf).ellipse([0, 0, 30 * SS, 16 * SS], fill=leaf)
        lf = lf.rotate(a, expand=True)
        art.alpha_composite(lf, (int((x - 15) * SS), int((y - 8) * SS)))
    art = finish(art, W, 150)
    b = bird(96, sleepy=dark)
    art.alpha_composite(b, ((W - 96) // 2, 112 - 92))
    f.file(art, "branch.png")
    f.image("branch", 0, 220, "branch.png")
    lines = [(p, f.file(hbar(140, 8, p, color=accent, done=done, track=track), f"line_{p}.png")) for p in range(0, 101, 10)]
    f.image_list("goal", (W - 140) // 2, 388, lines, SRC_STEP_PERCENT, 65)
    mid = digits("mid", 24, ink)
    f.number("steps", (W - mid[1] * 5) // 2, 404, mid, 5, SRC_STEPS, "5890", blanking=1)
    health_row(f, 446, digits("small", 16, soft))
    f.write()


def rings_face():
    """Two rings like an activity tracker: steps outside, active calories inside, the bird in the middle."""
    f = Face("fitwren_halka", "FitWren Halka")
    green, red = (0x9C, 0xE6, 0x5A), (0xFF, 0x52, 0x6E)
    hour = digits("time", 40, WHITE)
    colon = glyph("colon.png", ":", 40, GREY)
    tw = hour[1] * 4 + colon[1]
    tx = (W - tw) // 2
    f.number("hour", tx, 34, hour, 2, SRC_HOUR, "18")
    f.image("colon", tx + 2 * hour[1], 34, colon[0])
    f.number("minute", tx + 2 * hour[1] + colon[1], 34, hour, 2, SRC_MINUTE, "05")
    f.date_row(86, 16, GREY, WHITE)
    outer, inner = 170, 132
    y0 = 122
    f.image_list("steps_ring", (W - outer) // 2, y0,
                 [(p, f.file(ring(outer, 12, p, color=green, done=green, track=(0x1E, 0x33, 0x12)), f"outer_{p}.png")) for p in range(0, 101, 10)],
                 SRC_STEP_PERCENT, 75)
    f.image_list("kcal_ring", (W - inner) // 2, y0 + (outer - inner) // 2,
                 [(p, f.file(ring(inner, 12, p, color=red, done=red, track=(0x3A, 0x12, 0x1A)), f"inner_{p}.png")) for p in range(0, 101, 10)],
                 SRC_KCAL_PERCENT, 45)
    f.file(bird(78), "bird.png")
    f.image("bird", (W - 78) // 2, y0 + (outer - 78) // 2 - 4, "bird.png")
    g = digits("green", 24, green)
    r = digits("red", 24, red)
    f.file(icon_steps(20), "icon_steps.png")
    f.image("steps_icon", 26, 314, "icon_steps.png")
    f.number("steps", 52, 312, g, 5, SRC_STEPS, "7530", align=0, blanking=1)
    kcal = glyph("kcal.png", "kcal", 14, red)
    f.number("kcal", 52, 346, r, 4, SRC_KCAL, "245", align=0, blanking=1)
    f.image("kcal_label", 52 + r[1] * 4 + 2, 354, kcal[0])
    health_row(f, 400, digits("small", 18, GREY))
    f.write()


if __name__ == "__main__":
    classic()
    night()
    road()
    analog()
    neon()
    branch()
    branch(dark=True)
    rings_face()
    simple()
