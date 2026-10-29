r"""Builds FitWren's watch faces for the Xiaomi Smart Band 8 (192 x 490) as Mi Create projects.

Run: python3 make_faces.py  (needs Pillow). Each face gets its own folder <name>/ with
<name>.fprj, images/*.png and preview.png. Compile each on Windows with Mi Create's compiler:
    compile.exe -b <name>\<name>.fprj <name>\output <name>.face 1461256429
The bird is the same drawing as ui/BirdPainter.kt.
"""
import json
import math
import os
from xml.sax.saxutils import quoteattr

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

W, H = 192, 490
PREVIEW = (122, 310)
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
SRC_WEATHER = "3031"  # the band's condition code, see band/XiaomiWeather.kt
SRC_TEMP = "2031"  # °C

# The bird's mood by step goal percent: asleep, awake, happy, celebrating.
MOODS = ((0, "sleepy"), (25, "awake"), (60, "happy"), (100, "party"))


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


def light_digits(prefix, height, color):
    """Like digits(), in Inter Light."""
    global FONT
    bold, FONT = FONT, "/usr/share/fonts/opentype/inter/Inter-Light.otf"
    try:
        return digits(prefix, height, color)
    finally:
        FONT = bold


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


def bird(size, sleepy=False, mood=None):
    """The grown bird of BirdPainter (unit square 100 x 100) in one of MOODS."""
    mood = mood or ("sleepy" if sleepy else "happy")
    sleepy = mood == "sleepy"
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
    if mood == "awake":
        draw.polygon([pt(-5, -41), pt(5, -41), pt(0, -33)], fill=BEAK)
        img.alpha_composite(layer)
        return finish(img, size, size)
    if mood == "party":
        # confetti around the bird
        for i, (x, y) in enumerate(((8, 10), (90, 12), (14, 36), (92, 40), (6, 64), (94, 68), (30, 4), (70, 6))):
            color = ((0xEF, 0x53, 0x50), (0x29, 0xB6, 0xF6), (0x9C, 0xE6, 0x5A), (0xFF, 0x4F, 0xC3))[i % 4]
            if i % 2:
                draw.ellipse([x * u - 2.6 * u, y * u - 2.6 * u, x * u + 2.6 * u, y * u + 2.6 * u], fill=color)
            else:
                piece = Image.new("RGBA", layer.size, (0, 0, 0, 0))
                ImageDraw.Draw(piece).rectangle([x * u - 1.6 * u, y * u - 3.4 * u, x * u + 1.6 * u, y * u + 3.4 * u], fill=color)
                layer.alpha_composite(piece.rotate(30 * i, center=(x * u, y * u), resample=Image.BICUBIC))
        draw = ImageDraw.Draw(layer)
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


# Weather condition codes of the band (band/XiaomiWeather.kt) and the picture each one gets.
WEATHER_KINDS = {0: "sun", 1: "partly", 2: "cloud", 3: "rain", 4: "storm", 5: "storm", 6: "sleet", 7: "rain", 8: "rain",
                 9: "rain", 10: "rain", 11: "rain", 12: "rain", 13: "snow", 14: "snow", 15: "snow", 16: "snow",
                 17: "snow", 18: "fog", 19: "sleet"}
SUN = (0xFF, 0xC9, 0x3C)
CLOUD = (0xE0, 0xE6, 0xEE)
RAIN = (0x4F, 0xC3, 0xF7)


def icon_weather(kind, size, white=False):
    """A small weather picture; with [white] everything is white so the app can tint it."""
    img = canvas(size, size)
    d = ImageDraw.Draw(img)
    k = size * SS
    sun, cloud, rain = (WHITE, WHITE, WHITE) if white else (SUN, CLOUD, RAIN)

    def cloud_at(dx, dy, scale=1.0):
        for box in ((0.12, 0.42, 0.52, 0.82), (0.3, 0.26, 0.74, 0.7), (0.5, 0.42, 0.9, 0.82)):
            d.ellipse([(dx + box[0] * scale) * k, (dy + box[1] * scale) * k, (dx + box[2] * scale) * k, (dy + box[3] * scale) * k], fill=cloud)
        d.rectangle([(dx + 0.32 * scale) * k, (dy + 0.6 * scale) * k, (dx + 0.7 * scale) * k, (dy + 0.82 * scale) * k], fill=cloud)

    if kind in ("sun", "partly"):
        c, r = (0.5, 0.5) if kind == "sun" else (0.36, 0.34), (0.2 if kind == "sun" else 0.16)
        for i in range(8):
            a = math.radians(i * 45)
            d.line([((c[0] + math.cos(a) * (r + 0.07)) * k, (c[1] + math.sin(a) * (r + 0.07)) * k),
                    ((c[0] + math.cos(a) * (r + 0.17)) * k, (c[1] + math.sin(a) * (r + 0.17)) * k)], fill=sun, width=int(0.07 * k))
        d.ellipse([(c[0] - r) * k, (c[1] - r) * k, (c[0] + r) * k, (c[1] + r) * k], fill=sun)
        if kind == "partly":
            cloud_at(0.08, 0.12, 0.92)
    elif kind == "fog":
        for y in (0.3, 0.5, 0.7):
            d.line([(0.12 * k, y * k), (0.88 * k, y * k)], fill=cloud, width=int(0.09 * k))
    else:
        cloud_at(0, -0.14)
        if kind == "rain":
            for x in (0.32, 0.52, 0.72):
                d.line([(x * k, 0.76 * k), ((x - 0.06) * k, 0.94 * k)], fill=rain, width=int(0.07 * k))
        elif kind == "snow":
            for x in (0.3, 0.5, 0.7):
                d.ellipse([(x - 0.05) * k, 0.8 * k, (x + 0.05) * k, 0.9 * k], fill=cloud if white else WHITE)
        elif kind == "sleet":
            d.line([(0.34 * k, 0.76 * k), (0.28 * k, 0.94 * k)], fill=rain, width=int(0.07 * k))
            d.ellipse([0.5 * k, 0.8 * k, 0.6 * k, 0.9 * k], fill=cloud if white else WHITE)
        elif kind == "storm":
            d.polygon([(0.52 * k, 0.62 * k), (0.36 * k, 0.84 * k), (0.5 * k, 0.84 * k), (0.42 * k, 1.0 * k), (0.66 * k, 0.74 * k), (0.52 * k, 0.74 * k)], fill=sun)
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

    def __init__(self, name, title, face_id):
        global IMAGES
        self.face_id = face_id
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
            "Width": w, "Height": h, "Alpha": 255, "Alignment": 0, "DefaultIndex": 0, "Index_Src": source,
            "Spacing": 0, "Blanking": 0, "Visible_Src": 0,
        }, ("list", files, sample)))

    def mood_bird(self, size, x, y, sample):
        """The bird as a list that changes with the step goal: asleep, awake, happy, celebrating."""
        files = [(p, self.file(bird(size, mood=m), f"bird_{m}.png")) for p, m in MOODS]
        self.image_list("bird", x, y, files, SRC_STEP_PERCENT, sample)

    def weather(self, x, y, icon, digit_set, white=False):
        """The weather picture and the temperature with a degree sign; returns the sign's place."""
        names = {}
        for kind in sorted(set(WEATHER_KINDS.values())):
            names[kind] = self.file(icon_weather(kind, icon, white), f"weather_{kind}.png")
        self.image_list("weather", x, y + (digit_set[2] - icon) // 2, [(c, names[k]) for c, k in sorted(WEATHER_KINDS.items())], SRC_WEATHER, 1)
        nx = x + icon + 2
        self.number("temp", nx, y, digit_set, 2, SRC_TEMP, "18", align=0, blanking=1)
        return nx + 2 * digit_set[1], y

    def weather_badge(self, cx, y, height, color, prefix="temp", white=False):
        """The weather picture and the temperature, centred on cx; returns the degree sign's place."""
        temp = digits(prefix, height, color)
        deg = degree(temp, f"{prefix}_deg.png")
        x = cx - (height + 2 + 2 * temp[1] + deg[1]) // 2
        at = self.weather(x, y, height, temp, white)
        if not white:
            self.image("degree", *at, deg[0])
        return at, deg

    def date_row(self, y, height, day_color, num_color, gap=6, weather=False):
        """Weekday and date, with [weather] also the weather picture and the temperature after them."""
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
        icon = height
        if weather:
            deg = degree(date)
            total += gap + 4 + icon + 2 + 2 * date[1] + deg[1]
        x = (W - total) // 2
        self.image_list("weekday", x, y, [(i, f[0]) for i, f in enumerate(day_files)], SRC_WEEKDAY, 1)
        x += wd_w + gap
        self.number("day", x, y, date, 2, SRC_DAY, "26")
        self.image("date_dot", x + 2 * date[1], y, dot[0])
        self.number("month", x + 2 * date[1] + dot[1], y, date, 2, SRC_MONTH, "10")
        if weather:
            x += 4 * date[1] + dot[1] + gap + 4
            dx, dy = self.weather(x, y, icon, date)
            self.image("degree", dx, dy, deg[0])

    def flatten(self):
        """The Band 8 compiler stores plain images without transparency, so their clear pixels show
        black; lists and numbers keep it. So each plain image lying on a list is drawn into every
        picture of that list, and every other one gets the plain images under it drawn in."""
        below = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        kept = []
        for widget in self.widgets:
            a = widget.attrs
            if a["Shape"] != 30:
                kept.append(widget)
                continue
            file, x, y = a["Bitmap"], int(a["X"]), int(a["Y"])
            img = Image.open(os.path.join(IMAGES, file)).convert("RGBA")
            under = [k for k in kept if k.attrs["Shape"] != 30 and overlaps(k, img, x, y)]
            if under:
                host = under[0]
                hx, hy = int(host.attrs["X"]), int(host.attrs["Y"])
                inside = hx <= x and hy <= y and x + img.width <= hx + int(host.attrs["Width"]) and y + img.height <= hy + int(host.attrs["Height"])
                if len(under) > 1 or host.attrs["Shape"] != 31 or not inside:
                    raise ValueError(f"{self.name}: {a['Name']} lies on {[k.attrs['Name'] for k in under]}")
                for _, frame in host.sample[1]:
                    picture = Image.open(os.path.join(IMAGES, frame)).convert("RGBA")
                    picture.alpha_composite(img, (x - hx, y - hy))
                    picture.save(os.path.join(IMAGES, frame))
                continue
            flat = below.crop((x, y, x + img.width, y + img.height))
            flat.alpha_composite(img)
            flat.save(os.path.join(IMAGES, file))
            below.alpha_composite(img, (x, y))
            kept.append(widget)
        self.widgets = kept

    def draw(self, widgets, preview):
        """Draws [widgets] with their sample values onto [preview]."""

        def paste(file, x, y):
            preview.alpha_composite(Image.open(os.path.join(IMAGES, file)).convert("RGBA"), (x, y))

        for widget in widgets:
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
        return preview

    def write(self, shelf="faces"):
        self.flatten()
        preview = self.draw(self.widgets, Image.new("RGBA", (W, H), (0, 0, 0, 255)))
        preview.save(os.path.join(self.dir, "preview.png"))
        assets = os.path.join(HERE, "..", "app", "src", "main", "assets", shelf)
        if os.path.isdir(assets):
            preview.convert("RGB").save(os.path.join(assets, f"{self.name}.png"))
        # the compiler wants the Band 8 store preview at 122 x 310
        preview.resize(PREVIEW, Image.LANCZOS).save(os.path.join(IMAGES, "preview.png"))
        with open(os.path.join(self.dir, f"{self.name}.fprj"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write(f'<FaceProject DeviceType="9" Id="{self.face_id}">\n')
            f.write(f'  <Screen Title={quoteattr(self.title)} Bitmap="preview.png">\n')
            for w in self.widgets:
                f.write(w.xml() + "\n")
            f.write("  </Screen>\n</FaceProject>\n")
        if shelf == "faces":
            self.write_aod()

    def write_aod(self, color=(0xC8, 0xC8, 0xC8)):
        """The always-on screen: a dim one-line time and the date on black. Mi Create's compiler
        takes it from the AOD/ project inside the face's folder."""
        global IMAGES
        main_images = IMAGES
        folder = os.path.join(self.dir, "AOD")
        IMAGES = os.path.join(folder, "images")
        if os.path.isdir(IMAGES):
            for f in os.listdir(IMAGES):
                os.remove(os.path.join(IMAGES, f))
        os.makedirs(IMAGES, exist_ok=True)
        os.makedirs(os.path.join(folder, "output"), exist_ok=True)
        aod = Face.__new__(Face)
        aod.name, aod.title, aod.face_id, aod.dir = "AOD", "", "", folder
        aod.widgets, aod.sizes = [], {}
        time = light_digits("aod", 44, color)
        colon = glyph("aod_colon.png", ":", 44, (0x80, 0x80, 0x80))
        tx = (W - 4 * time[1] - colon[1]) // 2
        aod.number("hour", tx, 150, time, 2, SRC_HOUR, "09")
        aod.image("colon", tx + 2 * time[1], 150, colon[0])
        aod.number("minute", tx + 2 * time[1] + colon[1], 150, time, 2, SRC_MINUTE, "41")
        aod.date_row(214, 16, (0x80, 0x80, 0x80), (0xA0, 0xA0, 0xA0))
        aod.mood_bird(40, (W - 40) // 2, 270, 65)
        # plain images keep no transparency: give them the black under them
        for w in aod.widgets:
            if w.attrs["Shape"] == 30:
                path = os.path.join(IMAGES, w.attrs["Bitmap"])
                img = Image.open(path).convert("RGBA")
                flat = Image.new("RGBA", img.size, (0, 0, 0, 255))
                flat.alpha_composite(img)
                flat.save(path)
        aod.draw(aod.widgets, Image.new("RGBA", (W, H), (0, 0, 0, 255))).save(os.path.join(folder, "preview.png"))
        with open(os.path.join(folder, "AOD.fprj"), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write('<FaceProject DeviceType="9" Id="">\n')
            f.write('  <Screen Title="" Bitmap="">\n')
            for w in aod.widgets:
                f.write(w.xml() + "\n")
            f.write("  </Screen>\n</FaceProject>\n")
        IMAGES = main_images


def overlaps(widget, img, x, y):
    """Whether a list, number or dial draws inside the box of [img] placed at x, y; the band shows the whole box."""
    a = widget.attrs
    box = (int(a["X"]), int(a["Y"]), int(a["X"]) + int(a["Width"]), int(a["Y"]) + int(a["Height"]))
    mask = Image.new("L", (W, H), 0)
    if a["Shape"] == 31:
        for _, frame in widget.sample[1]:
            alpha = Image.open(os.path.join(IMAGES, frame)).convert("RGBA").getchannel("A")
            mask.paste(255, (box[0], box[1]), alpha.point(lambda v: 255 if v else 0))
    else:
        mask.paste(255, box)
    mine = Image.new("L", (W, H), 0)
    mine.paste(255, (x, y, x + img.width, y + img.height))
    return ImageChops.multiply(mask, mine).getbbox() is not None


def rings(face, size, width, **colors):
    return [(p, face.file(ring(size, width, p, **colors), f"ring_{p}.png")) for p in range(0, 101, 10)]


def degree(digit_set, name="deg.png"):
    """A degree sign cut from the digit set's zero, so it has the digits' colour."""
    zero = Image.open(os.path.join(IMAGES, digit_set[0][0])).convert("RGBA")
    h = digit_set[2]
    ring_ = zero.resize((max(4, int(digit_set[1] * 0.45)), max(4, int(h * 0.42))), Image.LANCZOS)
    img = Image.new("RGBA", (ring_.width + 2, h), (0, 0, 0, 0))
    img.alpha_composite(ring_, (1, 0))
    img.save(os.path.join(IMAGES, name))
    return name, img.width, h


def health_row(face, y, color, height=18):
    icon = 18
    digit_set = digits("small", height, color)
    face.file(icon_heart(icon), "icon_heart.png")
    face.file(icon_battery(icon), "icon_battery.png")
    face.image("heart_icon", 30, y, "icon_heart.png")
    face.number("heart", 30 + icon + 3, y, digit_set, 3, SRC_HEART, "72", align=0, blanking=1)
    face.image("battery_icon", 104, y, "icon_battery.png")
    face.number("battery", 104 + icon + 3, y, digit_set, 3, SRC_BATTERY, "85", align=0, blanking=1)


def classic():
    """Big two-line time, date, the bird inside the step goal ring, steps, heart rate and battery."""
    f = Face("fitwren", "FitWren", "266290001")
    hour = digits("hour", 64, BODY)
    minute = digits("minute", 64, WHITE)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 28, hour, 2, SRC_HOUR, "10")
    f.number("minute", hx, 98, minute, 2, SRC_MINUTE, "08")
    f.date_row(168, 20, BODY, WHITE)
    size = 156
    f.image_list("goal", (W - size) // 2, 196, rings(f, size, 10), SRC_STEP_PERCENT, 80)
    f.mood_bird(104, (W - 104) // 2, 196 + (size - 104) // 2 - 4, 80)
    f.weather_badge(W // 2, 360, 18, GREY)
    mid = digits("mid", 26, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 386, mid, 5, SRC_STEPS, "8421", blanking=1)
    health_row(f, 438, GREY)
    f.write()


def night():
    """Dark blue starry sky, a sleepy bird on the moon side, one-line time and a thin goal ring."""
    f = Face("fitwren_gece", "FitWren Gece", "266290002")
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
    f.weather_badge(W // 2, 26, 16, sky)
    size = 140
    f.image_list("goal", (W - size) // 2, 228, rings(f, size, 6, color=sky, done=(0xFF, 0xD5, 0x4F), track=(0x1E, 0x24, 0x44)), SRC_STEP_PERCENT, 60)
    f.mood_bird(96, (W - 96) // 2, 228 + (size - 96) // 2 - 2, 10)
    mid = digits("mid", 24, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 384, mid, 5, SRC_STEPS, "6230", blanking=1)
    health_row(f, 432, (0x8E, 0x9A, 0xC4))
    f.write()


def simple():
    """Only what matters: a very big time, the date, a thin goal line and a small bird at the bottom."""
    f = Face("fitwren_sade", "FitWren Sade", "266290009")
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
    f.weather_badge(W // 2, 346, 16, GREY)
    f.mood_bird(80, (W - 80) // 2, 372, 70)
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
    f = Face("fitwren_yol", "FitWren Yol", "266290003")
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
    f.widgets.insert(0, f.widgets.pop())  # the road lies under everything
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
    f.weather_badge(W // 2, 386, 16, GREY)
    f.number("steps", (W - mid[1] * 5) // 2, 412, mid, 5, SRC_STEPS, "6480", blanking=1)
    health_row(f, 452, GREY, 16)
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
    f = Face("fitwren_analog", "FitWren Analog", "266290004")
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
    f.mood_bird(70, (W - 70) // 2, cy - 30, 70)
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
    f.weather_badge(W // 2, 26, 16, GREY)
    size2 = 92
    f.image_list("goal", (W - size2) // 2, 296, rings(f, size2, 7), SRC_STEP_PERCENT, 70)
    f.file(icon_steps(26), "icon_steps.png")
    f.image("steps_icon", (W - 26) // 2, 296 + (size2 - 26) // 2, "icon_steps.png")
    mid = digits("mid", 26, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 400, mid, 5, SRC_STEPS, "7012", blanking=1)
    health_row(f, 444, GREY)
    f.write()


def neon():
    """Neon on black: glowing cyan and pink time, a pink step arc and a small bird."""
    f = Face("fitwren_neon", "FitWren Neon", "266290005")
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
    f.mood_bird(90, (W - 90) // 2, 218 + (size - 90) // 2 - 4, 55)
    mid = glow_digits("mid", 24, cyan, blur=3)
    f.number("steps", (W - mid[1] * 5 + 40) // 2, 374, mid, 5, SRC_STEPS, "5502", blanking=1, spacing=-10)
    f.weather_badge(W // 2, 412, 16, (0xB0, 0x9C, 0xC8))
    health_row(f, 438, (0xB0, 0x9C, 0xC8))
    f.write()


def branch(dark=False):
    """The bird sitting on a branch with leaves: a pastel morning, or the same scene at dusk."""
    if dark:
        f = Face("fitwren_dal_gece", "FitWren Dal Gece", "266290007")
        ink, accent, done, track, soft = (0xF2, 0xE6, 0xD8), (0xFF, 0xB0, 0x6A), (0x81, 0xC7, 0x84), (0x3A, 0x30, 0x3E), (0xB8, 0xA8, 0x9C)
        sky = background((0x1E, 0x17, 0x2E), (0x07, 0x05, 0x0D), stars=True)
        brown, leaf = (0x6D, 0x52, 0x48), (0x4E, 0x8F, 0x55)
    else:
        f = Face("fitwren_dal", "FitWren Dal", "266290006")
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
    f.file(art, "branch.png")
    f.image("branch", 0, 220, "branch.png")
    f.mood_bird(96, (W - 96) // 2, 220 + 112 - 92, 20 if dark else 65)
    f.weather_badge(162, 222, 16, soft)
    lines = [(p, f.file(hbar(140, 8, p, color=accent, done=done, track=track), f"line_{p}.png")) for p in range(0, 101, 10)]
    f.image_list("goal", (W - 140) // 2, 388, lines, SRC_STEP_PERCENT, 65)
    mid = digits("mid", 24, ink)
    f.number("steps", (W - mid[1] * 5) // 2, 404, mid, 5, SRC_STEPS, "5890", blanking=1)
    health_row(f, 446, soft, 16)
    f.write()


def rings_face():
    """Two rings like an activity tracker: steps outside, active calories inside, the bird in the middle."""
    f = Face("fitwren_halka", "FitWren Halka", "266290008")
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
    f.mood_bird(78, (W - 78) // 2, y0 + (outer - 78) // 2 - 4, 75)
    g = digits("green", 24, green)
    r = digits("red", 24, red)
    f.file(icon_steps(20), "icon_steps.png")
    f.image("steps_icon", 26, 314, "icon_steps.png")
    f.number("steps", 52, 312, g, 5, SRC_STEPS, "7530", align=0, blanking=1)
    kcal = glyph("kcal.png", "kcal", 14, red)
    f.number("kcal", 52, 346, r, 4, SRC_KCAL, "245", align=0, blanking=1)
    f.image("kcal_label", 52 + r[1] * 4 + 2, 354, kcal[0])
    f.weather_badge(W // 2, 374, 16, GREY)
    health_row(f, 400, GREY)
    f.write()


DIGIT_CELL = (64, 70)  # the designer template's hour and minute digits
DIGIT_STYLES = {
    "bold": "/usr/share/fonts/opentype/inter/Inter-Bold.otf",
    "light": "/usr/share/fonts/opentype/inter/Inter-Light.otf",
    "italic": "/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf",
    "serif": "/usr/share/fonts/truetype/crosextra/Caladea-Bold.ttf",
    "mono": "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Bold.ttf",
    "outline": "/usr/share/fonts/opentype/inter/Inter-Black.otf",
    "lcd": None,
}


def segment_digit(d, w, h):
    """A seven-segment digit, like an old digital watch."""
    on = ["abcedf", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg"][d]
    img = canvas(w, h)
    draw = ImageDraw.Draw(img)
    t, m = 9 * SS, 5 * SS  # thickness, margin
    x0, x1, y0, y1, ym = m, w * SS - m, m, h * SS - m, h * SS / 2
    bars = {
        "a": (x0 + t, y0, x1 - t, y0 + t), "g": (x0 + t, ym - t / 2, x1 - t, ym + t / 2), "d": (x0 + t, y1 - t, x1 - t, y1),
        "f": (x0, y0 + t, x0 + t, ym - t / 2), "b": (x1 - t, y0 + t, x1, ym - t / 2),
        "e": (x0, ym + t / 2, x0 + t, y1 - t), "c": (x1 - t, ym + t / 2, x1, y1 - t),
    }
    for k, box in bars.items():
        grow = SS * 1.5
        bx = (box[0] - grow, box[1], box[2] + grow, box[3]) if k in "agd" else (box[0], box[1] - grow, box[2], box[3] + grow)
        draw.rounded_rectangle(bx, radius=t / 2, fill=WHITE + (255,) if k in on else WHITE + (34,))
    return finish(img, w, h)


def style_strip(style):
    """The ten digits of [style], white, side by side in cells of DIGIT_CELL."""
    w, h = DIGIT_CELL
    strip = Image.new("RGBA", (w * 10, h), (0, 0, 0, 0))
    font_file = DIGIT_STYLES[style]
    for d in range(10):
        if font_file is None:
            strip.alpha_composite(segment_digit(d, w, h), (d * w, 0))
            continue
        size = 400
        font = ImageFont.truetype(font_file, size)
        zero = font.getbbox("0")
        size = int(size * (h - 6) / (zero[3] - zero[1]))
        widest = max(ImageFont.truetype(font_file, size).getbbox(str(k))[2] - ImageFont.truetype(font_file, size).getbbox(str(k))[0] for k in range(10))
        if widest > w - 4:
            size = int(size * (w - 4) / widest)
        font = ImageFont.truetype(font_file, size * SS)
        img = canvas(w, h)
        draw = ImageDraw.Draw(img)
        box = font.getbbox(str(d))
        line = font.getbbox("0")
        x = (w * SS - (box[2] - box[0])) / 2 - box[0]
        y = (h * SS - (line[3] - line[1])) / 2 - line[1]
        if style == "outline":
            draw.text((x, y), str(d), font=font, fill=WHITE, stroke_width=3 * SS, stroke_fill=WHITE)
            draw.text((x, y), str(d), font=font, fill=(255, 255, 255, 0))
        else:
            draw.text((x, y), str(d), font=font, fill=WHITE)
        strip.alpha_composite(finish(img, w, h), (d * w, 0))
    return strip


def designer():
    """The template behind the app's face designer. Everything here is white on black: the app
    tints the digits, draws its own goal bars and background, and draws the layers below onto
    the background (the Band 8 compiler keeps no transparency in plain images)."""
    f = Face("fitwren_tasarim", "FitWren Tasarım", "266291000")
    f.file(Image.new("RGBA", (W, H), (0, 0, 0, 255)), "background.png")
    f.image("background", 0, 0, "background.png")
    hour = digits("hour", 70, WHITE)
    minute = digits("minute", 70, WHITE)
    hx = (W - 2 * hour[1]) // 2
    f.number("hour", hx, 34, hour, 2, SRC_HOUR, "09")
    f.number("minute", hx, 112, minute, 2, SRC_MINUTE, "41")
    height, gap, y = 18, 6, 194
    date = digits("date", height, WHITE)
    days = [glyph(f"weekday_{i}.png", n, height, WHITE) for i, n in enumerate(WEEKDAYS)]
    wd_w = max(d[1] for d in days)
    for d in days:
        img = Image.open(os.path.join(IMAGES, d[0]))
        padded = Image.new("RGBA", (wd_w, height), (0, 0, 0, 0))
        padded.alpha_composite(img, (wd_w - img.size[0], 0))
        padded.save(os.path.join(IMAGES, d[0]))
    dot = glyph("dot.png", ".", height, WHITE)
    x = (W - (wd_w + gap + date[1] * 4 + dot[1])) // 2
    f.image_list("weekday", x, y, [(i, d[0]) for i, d in enumerate(days)], SRC_WEEKDAY, 1)
    x += wd_w + gap
    f.number("day", x, y, date, 2, SRC_DAY, "26")
    dot_at = (x + 2 * date[1], y)
    f.number("month", x + 2 * date[1] + dot[1], y, date, 2, SRC_MONTH, "10")
    bar_w, bar_h, bar_y = 140, 8, 356
    lines = [(p, f.file(hbar(bar_w, bar_h, p, color=WHITE, done=WHITE, track=(0x40, 0x40, 0x40)), f"line_{p}.png")) for p in range(0, 101, 10)]
    f.image_list("goal", (W - bar_w) // 2, bar_y, lines, SRC_STEP_PERCENT, 65)
    mid = digits("mid", 26, WHITE)
    f.number("steps", (W - mid[1] * 5) // 2, 372, mid, 5, SRC_STEPS, "5890", blanking=1)
    # the weather gets digits of its own, so it can be hidden apart from the health row
    deg_at, deg = f.weather_badge(W // 2, 404, 16, WHITE, white=True)
    small = digits("small", 18, WHITE)
    f.number("heart", 30 + 18 + 3, 428, small, 3, SRC_HEART, "72", align=0, blanking=1)
    f.number("battery", 104 + 18 + 3, 428, small, 3, SRC_BATTERY, "85", align=0, blanking=1)
    bird_at = ((W - 96) // 2, 250)
    f.mood_bird(96, *bird_at, 65)
    f.write(shelf="designer")
    # the layers the app draws onto the background, with their transparency; each belongs to a
    # part the user can hide and to a block the user can move
    shelf = os.path.join(HERE, "..", "app", "src", "main", "assets", "designer")
    layers = [
        ("dot.png", Image.open(os.path.join(IMAGES, dot[0])), *dot_at, "date", "time"),
        ("deg.png", Image.open(os.path.join(IMAGES, deg[0])), *deg_at, "weather", "stats"),
        ("icon_heart.png", icon_heart(18), 30, 428, "health", "stats"),
        ("icon_battery.png", icon_battery(18), 104, 428, "health", "stats"),
    ]
    for _, m in MOODS:
        bird(96, mood=m).save(os.path.join(shelf, f"bird_{m}.png"))
    for old in ("bird.png", "bird_sleepy.png"):
        if os.path.exists(os.path.join(shelf, old)):
            os.remove(os.path.join(shelf, old))
    for name, img, *_ in layers:
        img.save(os.path.join(shelf, name))
    for style in DIGIT_STYLES:
        style_strip(style).save(os.path.join(shelf, f"digits_{style}.png"))
    # each widget's part (what its digits are coloured and hidden by) and block
    parts = {"hour": "hour", "minute": "minute", "weekday": "date", "day": "date", "month": "date",
             "goal": "bar", "steps": "steps", "heart": "health", "battery": "health",
             "weather": "weather", "temp": "weather", "bird": "bird"}
    blocks = {"hour": "time", "minute": "time", "weekday": "time", "day": "time", "month": "time",
              "bird": "bird"}
    # sample text of each part, for the app's preview
    for part in ("date", "steps", "health", "weather"):
        chosen = [w for w in f.widgets if parts.get(w.attrs["Name"]) == part]
        f.draw(chosen, Image.new("RGBA", (W, H), (0, 0, 0, 0))).save(os.path.join(shelf, f"sample_{part}.png"))
    for old in ("sample_hour.png", "sample_minute.png", "sample_text.png"):
        if os.path.exists(os.path.join(shelf, old)):
            os.remove(os.path.join(shelf, old))
    order = []  # list pictures in the order the compiler stores them: first use in the project
    for w in f.widgets:
        files = w.attrs.get("BitmapList")
        if files and files not in [o[1] for o in order]:
            order.append((parts[w.attrs["Name"]], files))
    with open(os.path.join(shelf, "layout.json"), "w", encoding="utf-8") as out:
        json.dump({
            "layers": [{"file": n, "x": x, "y": y, "part": part, "block": block} for n, _, x, y, part, block in layers],
            "lists": [o[0] for o in order],
            # widgets in the order the compiler stores them, with the block each moves with
            "widgets": [{"name": w.attrs["Name"], "block": blocks.get(w.attrs["Name"], "stats" if w.attrs["Name"] in parts else "")} for w in f.widgets],
            "time": {"x": hx, "hour": 34, "minute": 112, "cell": list(DIGIT_CELL)},
            "bar": {"x": (W - bar_w) // 2, "y": bar_y, "width": bar_w, "height": bar_h, "sample": 60},
            "bird": {"x": bird_at[0], "y": bird_at[1], "moods": [m for _, m in MOODS]},
            "styles": list(DIGIT_STYLES),
        }, out, indent=1)


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
    designer()
