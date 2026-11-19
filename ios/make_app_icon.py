"""Generate a 1024x1024 Atlas app icon (dark observatory + gold meridian) with no external deps.

Run: python ios/make_app_icon.py
"""
import math
import os
import struct
import zlib

SIZE = 1024
BG = (11, 13, 18)
GOLD = (229, 168, 61)
BRIGHT = (245, 201, 107)
DIM = (154, 116, 32)


def write_png(path, width, height, pixels):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data +
                struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter: none
        for x in range(width):
            raw.extend(pixels[y * width + x])
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def blend(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    cx = cy = SIZE / 2
    pixels = [BG] * (SIZE * SIZE)
    # soft vertical aura
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy) / (SIZE * 0.55)
            if d < 1:
                pixels[y * SIZE + x] = blend(BG, (27, 34, 51), (1 - d) ** 2 * 0.8)

    def put(x, y, color, alpha=1.0):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            i = y * SIZE + x
            pixels[i] = blend(pixels[i], color, alpha)

    # Tilted dashed orbit ellipse — the Meridian motif
    rx, ry = SIZE * 0.31, SIZE * 0.155
    tilt = math.radians(-24)
    for k in range(720):
        a = 2 * math.pi * k / 720
        if (k // 12) % 2 == 0:
            ex, ey = rx * math.cos(a), ry * math.sin(a)
            x = cx + ex * math.cos(tilt) - ey * math.sin(tilt)
            y = cy + ex * math.sin(tilt) + ey * math.cos(tilt)
            for dy in range(-3, 4):
                for dx in range(-3, 4):
                    if dx * dx + dy * dy <= 9:
                        put(int(x + dx), int(y + dy), DIM, 0.9)

    # Main gold progress arc, from -90 degrees, sweeping 250 degrees
    ring_r = SIZE * 0.235
    for k in range(1000):
        a = math.radians(-90 + 250 * k / 1000)
        x = cx + ring_r * math.cos(a)
        y = cy + ring_r * math.sin(a)
        for dy in range(-9, 10):
            for dx in range(-9, 10):
                d2 = dx * dx + dy * dy
                if 25 <= d2 <= 81:
                    put(int(x + dx), int(y + dy), GOLD, 0.95)

    # Leading dot
    a = math.radians(-90 + 250)
    lx, ly = cx + ring_r * math.cos(a), cy + ring_r * math.sin(a)
    for y in range(int(ly) - 22, int(ly) + 23):
        for x in range(int(lx) - 22, int(lx) + 23):
            if (x - lx) ** 2 + (y - ly) ** 2 <= 22 * 22:
                put(x, y, BRIGHT)

    # Center monogram A
    for y in range(int(cy - 110), int(cy + 110)):
        for x in range(int(cx - 100), int(cx + 100)):
            t = (y - (cy + 100)) / 210.0
            left = cx - 92 * t
            right = cx + 92 * t
            cross_y = cy + 30
            on_stroke = abs(x - left) < 13 or abs(x - right) < 13
            on_cross = abs(y - cross_y) < 11 and left + 24 <= x <= right - 24
            if on_stroke or on_cross:
                put(x, y, BRIGHT, 0.96)

    out = os.path.join(os.path.dirname(__file__), "AtlasMusic", "Resources",
                       "Assets.xcassets", "AppIcon.appiconset", "icon_1024.png")
    write_png(out, SIZE, SIZE, pixels)
    print("OK", out)


if __name__ == "__main__":
    main()
