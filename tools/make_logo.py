#!/usr/bin/env python3
"""Generates the Attendance Tracker launcher icons (pure stdlib, no PIL)."""
import math
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "app", "src", "main", "res")

INDIGO = (79, 70, 229)
CYAN = (6, 182, 212)
MINT_A = (52, 211, 153)
MINT_B = (16, 185, 129)
DEEP = (7, 11, 20)
INK = (8, 20, 34)
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t)


def mix(dst, src, alpha):
    return (dst[0] + (src[0] - dst[0]) * alpha,
            dst[1] + (src[1] - dst[1]) * alpha,
            dst[2] + (src[2] - dst[2]) * alpha)


def capsule(px, py, ax, ay, bx, by, r):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    length2 = vx * vx + vy * vy
    t = 0.0 if length2 == 0 else max(0.0, min(1.0, (wx * vx + wy * vy) / length2))
    dx, dy = px - (ax + vx * t), py - (ay + vy * t)
    return math.hypot(dx, dy) <= r


def sample(x, y):
    """Returns (r,g,b,a) for a point in 0..1 space."""
    qx = abs(x - 0.5) - (0.5 - 0.22)
    qy = abs(y - 0.5) - (0.5 - 0.22)
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - 0.22
    if outside > 0:
        return (0, 0, 0, 0)

    t = max(0.0, min(1.0, (x * 0.62 + y * 0.55)))
    color = lerp(INDIGO, CYAN, t)

    # soft highlight in the upper left
    d = math.hypot(x - 0.28, y - 0.2)
    if d < 0.6:
        color = mix(color, WHITE, 0.16 * (1.0 - d / 0.6))

    # clock ring
    cx, cy, rad = 0.5, 0.452, 0.238
    dist = math.hypot(x - cx, y - cy)
    if abs(dist - rad) <= 0.030:
        color = WHITE
    # hands
    elif capsule(x, y, cx, cy, cx, cy - 0.125, 0.027):
        color = WHITE
    elif capsule(x, y, cx, cy, cx + 0.105, cy + 0.062, 0.027):
        color = WHITE
    elif dist <= 0.030:
        color = WHITE

    # badge with check mark
    bx, by, br = 0.722, 0.718, 0.172
    bdist = math.hypot(x - bx, y - by)
    if bdist <= br + 0.020:
        color = DEEP
    if bdist <= br:
        color = lerp(MINT_A, MINT_B, max(0.0, min(1.0, (y - (by - br)) / (2 * br))))
    if capsule(x, y, 0.652, 0.718, 0.700, 0.766, 0.036):
        color = INK
    if capsule(x, y, 0.700, 0.766, 0.792, 0.662, 0.036):
        color = INK

    return (color[0], color[1], color[2], 255)


def render(size, ss=3):
    buf = bytearray(size * size * 4)
    inv = 1.0 / size
    step = 1.0 / ss
    idx = 0
    for py in range(size):
        for px in range(size):
            r = g = b = a = 0
            for sy in range(ss):
                yy = (py + (sy + 0.5) * step) * inv
                for sx in range(ss):
                    xx = (px + (sx + 0.5) * step) * inv
                    cr, cg, cb, ca = sample(xx, yy)
                    r += cr * ca
                    g += cg * ca
                    b += cb * ca
                    a += ca
            n = ss * ss
            if a > 0:
                buf[idx] = int(r / a)
                buf[idx + 1] = int(g / a)
                buf[idx + 2] = int(b / a)
                buf[idx + 3] = int(a / n)
            idx += 4
    return buf


def downsample(src, src_size, dst_size, round_mask):
    dst = bytearray(dst_size * dst_size * 4)
    scale = src_size / dst_size
    idx = 0
    for y in range(dst_size):
        y0 = y * scale
        y1 = y0 + scale
        for x in range(dst_size):
            x0 = x * scale
            x1 = x0 + scale
            r = g = b = a = 0.0
            count = 0
            sy = int(y0)
            while sy < y1:
                sy_next = min(int(sy + 1), src_size)
                sx = int(x0)
                while sx < x1:
                    sx_next = min(int(sx + 1), src_size)
                    o = (sy * src_size + sx) * 4
                    weight = (min(sy + 1, y1) - sy) * (min(sx + 1, x1) - sx)
                    r += src[o] * src[o + 3] * weight
                    g += src[o + 1] * src[o + 3] * weight
                    b += src[o + 2] * src[o + 3] * weight
                    a += src[o + 3] * weight
                    count += weight
                    sx = sx_next
                sy = sy_next
            if a > 0:
                dst[idx] = int(r / a)
                dst[idx + 1] = int(g / a)
                dst[idx + 2] = int(b / a)
                dst[idx + 3] = int(a / count)
            if round_mask:
                dst[idx + 3] = int(dst[idx + 3] * circle_coverage(
                    (x + 0.5) / dst_size, (y + 0.5) / dst_size, 1.0 / dst_size))
            idx += 4
    return dst


def circle_coverage(cx, cy, px):
    ss = 4
    hits = 0
    for sy in range(ss):
        for sx in range(ss):
            x = cx + (sx + 0.5) / ss * px - px / 2
            y = cy + (sy + 0.5) / ss * px - px / 2
            if math.hypot(x - 0.5, y - 0.5) <= 0.5:
                hits += 1
    return hits / (ss * ss)


def write_png(path, size, data):
    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)
        raw.extend(data[y * stride:(y + 1) * stride])

    def chunk(tag, payload):
        out = struct.pack(">I", len(payload)) + tag + payload
        out += struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
        return out

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    master_size = 512
    print("rendering master", master_size)
    master = render(master_size, ss=2)

    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192,
    }

    for name, size in densities.items():
        folder = os.path.join(OUT, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        square = downsample(master, master_size, size, False)
        write_png(os.path.join(folder, "ic_launcher.png"), size, square)
        rounded = downsample(master, master_size, size, True)
        write_png(os.path.join(folder, "ic_launcher_round.png"), size, rounded)
        print("wrote", folder, size)

    # 512 master used in the README / store listing
    write_png(os.path.join(OUT, "..", "..", "..", "..", "logo_512.png"),
              master_size, master)


if __name__ == "__main__":
    main()
