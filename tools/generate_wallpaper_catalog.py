#!/usr/bin/env python3
"""Generate the original ApexStudio wallpaper pack and its serializable manifest.

Art is made from gradients and geometric shapes with Pillow; no third-party images,
fonts, or network assets are used. The fixed ZIP timestamps and sorted entries make
pack hashes reproducible when the same Pillow/JPEG encoder version is used.
"""
from __future__ import annotations

import hashlib
import json
import math
import random
import zipfile
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
WIDTH, HEIGHT = 1920, 1080
PACK_ID = "apexstudio-wallpapers-vol-01"
PACK_VERSION = 1
LICENSE = "ApexStudio original procedural artwork; no third-party assets."
REMOTE_DIR = ROOT / "remote-assets" / "backgrounds"
APP_CATALOG = ROOT / "app" / "src" / "main" / "assets" / "backgrounds" / "wallpapers.json"
ZIP_PATH = REMOTE_DIR / "apexstudio-wallpapers-vol-01.zip"

# id, display name, category, art recipe
ITEMS = [
    ("cyber-portal", "Cyber Portal", "Abstract", "portal"),
    ("neon-city", "Neon City", "City", "city"),
    ("virtual-studio", "Virtual Studio", "Studio", "studio"),
    ("deep-space", "Deep Space", "Space", "space"),
    ("matrix-grid", "Matrix Grid", "Abstract", "matrix"),
    ("aurora-borealis", "Aurora Borealis", "Nature", "aurora"),
    ("ocean-dusk", "Ocean Dusk", "Nature", "ocean"),
    ("sandstone-sunrise", "Sandstone Sunrise", "Nature", "desert"),
    ("cinema-bokeh", "Cinema Bokeh", "Studio", "bokeh"),
    ("rose-gold", "Rose Gold", "Abstract", "rosegold"),
]


def rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def gradient(stops: Iterable[tuple[float, tuple[int, int, int]]]) -> Image.Image:
    stops = list(stops)
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    for y in range(HEIGHT):
        t = y / (HEIGHT - 1)
        lower = next((i for i in range(1, len(stops)) if t <= stops[i][0]), len(stops) - 1)
        t0, c0 = stops[lower - 1]
        t1, c1 = stops[lower]
        k = (t - t0) / max(1e-6, t1 - t0)
        color = tuple(round(a * (1 - k) + b * k) for a, b in zip(c0, c1))
        for x in range(WIDTH):
            pixels[x, y] = color
    return image


def overlay_glows(image: Image.Image, glows: list[tuple[int, int, int, tuple[int, int, int], int]]) -> Image.Image:
    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for x, y, radius, color, alpha in glows:
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(100))
    return Image.alpha_composite(image.convert("RGBA"), layer).convert("RGB")


def add_fine_grain(image: Image.Image, seed: int) -> Image.Image:
    noise = Image.effect_noise(image.size, 12).convert("L")
    noise_rgb = Image.merge("RGB", (noise, noise, noise))
    # A very light mix prevents banding without distracting from a foreground subject.
    return Image.blend(image, noise_rgb, 0.035)


def draw_portal() -> Image.Image:
    im = gradient([(0, rgb("#030817")), (0.55, rgb("#101044")), (1, rgb("#070711"))])
    im = overlay_glows(im, [(930, 500, 430, rgb("#00d9ff"), 75), (1120, 530, 340, rgb("#d42cff"), 65)])
    d = ImageDraw.Draw(im, "RGBA")
    cx, cy = 960, 530
    for i in range(10, 0, -1):
        rx, ry = 125 + i * 42, 205 + i * 25
        d.ellipse((cx-rx, cy-ry, cx+rx, cy+ry), outline=(65, 220, 255, max(15, 95-i*6)), width=max(2, 8-i//2))
    d.ellipse((cx-104, cy-178, cx+104, cy+178), fill=(4, 12, 34, 180), outline=(255, 70, 210, 170), width=5)
    return im


def draw_city() -> Image.Image:
    im = gradient([(0, rgb("#080a2c")), (0.58, rgb("#351460")), (1, rgb("#fa5b81"))])
    im = overlay_glows(im, [(360, 550, 260, rgb("#ff4c99"), 65), (1480, 470, 340, rgb("#574aff"), 65)])
    d = ImageDraw.Draw(im, "RGBA")
    horizon = 745
    rng = random.Random(14)
    x = -20
    while x < WIDTH:
        w, h = rng.randint(95, 210), rng.randint(160, 470)
        top = horizon - h
        d.rectangle((x, top, x+w, HEIGHT), fill=(10, 10, 34, 238))
        for wx in range(x + 18, x + w - 12, 28):
            for wy in range(top + 26, horizon - 15, 37):
                if rng.random() < 0.38:
                    d.rounded_rectangle((wx, wy, wx+10, wy+17), radius=3, fill=(80, 228, 255, rng.randint(65, 160)))
                elif rng.random() < 0.12:
                    d.rectangle((wx, wy, wx+8, wy+15), fill=(255, 98, 178, 130))
        x += w - rng.randint(4, 18)
    d.line((0, horizon, WIDTH, horizon), fill=(83, 226, 255, 210), width=5)
    return im


def draw_studio() -> Image.Image:
    im = gradient([(0, rgb("#060d22")), (0.64, rgb("#183b66")), (1, rgb("#070c18"))])
    im = overlay_glows(im, [(960, 420, 420, rgb("#2196f3"), 82), (960, 820, 520, rgb("#19c6d8"), 38)])
    d = ImageDraw.Draw(im, "RGBA")
    d.polygon([(0, 760), (WIDTH, 760), (WIDTH, HEIGHT), (0, HEIGHT)], fill=(3, 9, 23, 140))
    d.line((0, 758, WIDTH, 758), fill=(104, 218, 255, 180), width=4)
    for x in range(-400, WIDTH+600, 180):
        d.line((960, 760, x, HEIGHT), fill=(70, 178, 228, 60), width=2)
    for i in range(1, 8):
        y = 760 + i * i * 6
        d.line((0, y, WIDTH, y), fill=(70, 178, 228, max(8, 45-i*4)), width=2)
    return im


def draw_space() -> Image.Image:
    im = gradient([(0, rgb("#050611")), (0.6, rgb("#17123c")), (1, rgb("#03040d"))])
    im = overlay_glows(im, [(520, 420, 420, rgb("#9d31c7"), 84), (1270, 390, 500, rgb("#315bd6"), 70), (1000, 680, 260, rgb("#e8518b"), 34)])
    d = ImageDraw.Draw(im, "RGBA")
    rng = random.Random(28)
    for _ in range(300):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        radius = rng.choice([1, 2, 2, 3])
        alpha = rng.randint(45, 190)
        d.ellipse((x-radius, y-radius, x+radius, y+radius), fill=(220, 234, 255, alpha))
    return im


def draw_matrix() -> Image.Image:
    im = gradient([(0, rgb("#020c0a")), (0.52, rgb("#001d16")), (1, rgb("#020804"))])
    im = overlay_glows(im, [(390, 450, 420, rgb("#00e88a"), 48), (1510, 640, 500, rgb("#19b86e"), 44)])
    d = ImageDraw.Draw(im, "RGBA")
    for x in range(0, WIDTH, 80):
        d.line((x, 0, x, HEIGHT), fill=(20, 255, 140, 45), width=2)
    for y in range(0, HEIGHT, 80):
        d.line((0, y, WIDTH, y), fill=(20, 255, 140, 34), width=2)
    rng = random.Random(5)
    for _ in range(95):
        x = rng.randrange(WIDTH)
        y = rng.randrange(HEIGHT)
        length = rng.randrange(35, 150)
        d.line((x, y, x, min(HEIGHT, y+length)), fill=(31, 255, 143, rng.randint(35, 145)), width=rng.choice([2, 3, 4]))
    return im


def draw_aurora() -> Image.Image:
    im = gradient([(0, rgb("#07152d")), (0.62, rgb("#102447")), (1, rgb("#061019"))])
    aurora = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    d = ImageDraw.Draw(aurora, "RGBA")
    d.polygon([(-100, 250), (120, 190), (430, 340), (760, 150), (1020, 310), (1390, 120), (1750, 300), (2050, 170), (2050, 690), (0, 640)], fill=(22, 238, 176, 80))
    d.polygon([(-100, 370), (260, 260), (560, 450), (920, 250), (1190, 420), (1540, 240), (1900, 390), (2050, 300), (2050, 700), (0, 720)], fill=(70, 144, 255, 67))
    aurora = aurora.filter(ImageFilter.GaussianBlur(82))
    im = Image.alpha_composite(im.convert("RGBA"), aurora).convert("RGB")
    d = ImageDraw.Draw(im, "RGBA")
    d.polygon([(0, 840), (210, 700), (420, 865), (710, 670), (1030, 870), (1350, 690), (1650, 860), (1900, 720), (WIDTH, 870), (WIDTH, HEIGHT), (0, HEIGHT)], fill=(3, 14, 17, 245))
    return im


def draw_ocean() -> Image.Image:
    im = gradient([(0, rgb("#121a3a")), (0.47, rgb("#bd6474")), (0.61, rgb("#f4a96a")), (1, rgb("#071b2b"))])
    im = overlay_glows(im, [(970, 570, 270, rgb("#ffd58a"), 75)])
    d = ImageDraw.Draw(im, "RGBA")
    horizon = 610
    d.line((0, horizon, WIDTH, horizon), fill=(255, 216, 168, 150), width=3)
    for i in range(32):
        y = horizon + 12 + i * i * 1.0
        width = max(40, 700 - i * 19)
        alpha = max(10, 95 - i * 2)
        d.line((960-width/2, y, 960+width/2, y), fill=(255, 177, 122, alpha), width=max(1, 5-i//7))
    rng = random.Random(42)
    for _ in range(180):
        y = rng.randint(horizon+30, HEIGHT-10)
        x = rng.randrange(WIDTH)
        w = rng.randint(15, 150)
        d.line((x, y, x+w, y), fill=(115, 205, 216, rng.randint(14, 75)), width=2)
    return im


def draw_desert() -> Image.Image:
    im = gradient([(0, rgb("#543a58")), (0.52, rgb("#dc876f")), (1, rgb("#f7ca8b"))])
    im = overlay_glows(im, [(1430, 360, 260, rgb("#ffdf9c"), 95)])
    d = ImageDraw.Draw(im, "RGBA")
    d.ellipse((1320, 250, 1540, 470), fill=(255, 219, 142, 220))
    d.polygon([(0, 735), (320, 595), (690, 700), (1030, 560), (1410, 700), (1700, 590), (WIDTH, 710), (WIDTH, HEIGHT), (0, HEIGHT)], fill=(128, 70, 73, 245))
    d.polygon([(0, 865), (420, 720), (790, 850), (1170, 680), (1550, 835), (WIDTH, 710), (WIDTH, HEIGHT), (0, HEIGHT)], fill=(197, 111, 82, 250))
    d.polygon([(0, 980), (500, 835), (900, 955), (1350, 810), (WIDTH, 950), (WIDTH, HEIGHT), (0, HEIGHT)], fill=(233, 157, 102, 255))
    return im


def draw_bokeh() -> Image.Image:
    im = gradient([(0, rgb("#080b18")), (0.58, rgb("#2c1d31")), (1, rgb("#0d1020"))])
    glow_layer = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow_layer, "RGBA")
    rng = random.Random(73)
    colors = [(255, 174, 106), (255, 103, 141), (141, 113, 255), (69, 211, 224), (255, 221, 150)]
    for _ in range(70):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        r = rng.randint(18, 105)
        color = rng.choice(colors)
        d.ellipse((x-r, y-r, x+r, y+r), fill=(*color, rng.randint(20, 80)))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(55))
    im = Image.alpha_composite(im.convert("RGBA"), glow_layer).convert("RGB")
    crisp = ImageDraw.Draw(im, "RGBA")
    for _ in range(50):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        r = rng.randint(3, 13)
        c = rng.choice(colors)
        crisp.ellipse((x-r, y-r, x+r, y+r), fill=(*c, rng.randint(50, 135)))
    return im


def draw_rosegold() -> Image.Image:
    im = gradient([(0, rgb("#281c35")), (0.35, rgb("#80556c")), (0.72, rgb("#e8a98d")), (1, rgb("#361d37"))])
    im = overlay_glows(im, [(330, 330, 390, rgb("#fbd0a7"), 54), (1510, 770, 470, rgb("#c05ca0"), 55)])
    d = ImageDraw.Draw(im, "RGBA")
    d.polygon([(0, 170), (420, 0), (950, 0), (470, 430), (0, 540)], fill=(255, 225, 197, 28))
    d.polygon([(1100, HEIGHT), (WIDTH, 430), (WIDTH, 770), (1410, HEIGHT)], fill=(255, 222, 190, 30))
    d.ellipse((680, 210, 1240, 770), outline=(255, 228, 211, 86), width=3)
    d.ellipse((735, 265, 1185, 715), outline=(255, 228, 211, 53), width=2)
    return im


RECIPES = {
    "portal": draw_portal,
    "city": draw_city,
    "studio": draw_studio,
    "space": draw_space,
    "matrix": draw_matrix,
    "aurora": draw_aurora,
    "ocean": draw_ocean,
    "desert": draw_desert,
    "bokeh": draw_bokeh,
    "rosegold": draw_rosegold,
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    REMOTE_DIR.mkdir(parents=True, exist_ok=True)
    APP_CATALOG.parent.mkdir(parents=True, exist_ok=True)
    files: list[tuple[str, bytes]] = []
    entries = []
    for wallpaper_id, name, category, recipe in ITEMS:
        image = RECIPES[recipe]()
        from io import BytesIO
        buf = BytesIO()
        image.save(buf, format="JPEG", quality=86, optimize=True, progressive=True, subsampling=1)
        payload = buf.getvalue()
        path = f"wallpapers/{wallpaper_id}.jpg"
        files.append((path, payload))
        entries.append({
            "id": wallpaper_id,
            "name": name,
            "category": category,
            "assetPath": path,
            "width": WIDTH,
            "height": HEIGHT,
            "sizeBytes": len(payload),
            "sha256": sha256(payload),
            "license": LICENSE,
        })

    catalog = {
        "schemaVersion": 1,
        "packId": PACK_ID,
        "packVersion": PACK_VERSION,
        "license": LICENSE,
        "wallpapers": entries,
    }
    catalog_bytes = (json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode()
    APP_CATALOG.write_bytes(catalog_bytes)

    with zipfile.ZipFile(ZIP_PATH, "w") as archive:
        manifest_info = zipfile.ZipInfo("manifest.json", (2020, 1, 1, 0, 0, 0))
        manifest_info.compress_type = zipfile.ZIP_DEFLATED
        manifest_info.external_attr = 0o644 << 16
        archive.writestr(manifest_info, catalog_bytes)
        for path, payload in sorted(files):
            info = zipfile.ZipInfo(path, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            archive.writestr(info, payload)

    archive_bytes = ZIP_PATH.read_bytes()
    print(json.dumps({
        "packId": PACK_ID,
        "itemCount": len(entries),
        "sizeBytes": len(archive_bytes),
        "sha256": sha256(archive_bytes),
        "entry": "manifest.json",
        "entrySha256": sha256(catalog_bytes),
        "wallpapers": [{"id": e["id"], "sizeBytes": e["sizeBytes"], "sha256": e["sha256"]} for e in entries],
    }, indent=2))


if __name__ == "__main__":
    main()
