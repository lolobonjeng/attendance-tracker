#!/usr/bin/env python3
"""Renders the manual's display list to PNG so the layout can be eyeballed.

This is a geometry preview, not a PDF renderer: text is drawn as a solid bar of
the exact measured width, so what you see is the space the text occupies. It is
enough to judge spacing, alignment and whether labels fit inside their boxes.

Usage:
    python3 tools/preview_manual.py [outdir]
"""

import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_manual as M  # noqa: E402

SCALE = 2
PW = int(M.W * SCALE)
PH = int(M.H * SCALE)


def blank():
    return bytearray(b"\xff" * (PW * PH * 3))


def put(buf, x, y, color, alpha=1.0):
    if x < 0 or y < 0 or x >= PW or y >= PH:
        return
    o = (y * PW + x) * 3
    if alpha >= 1.0:
        buf[o] = color[0]
        buf[o + 1] = color[1]
        buf[o + 2] = color[2]
    else:
        for i in range(3):
            buf[o + i] = int(buf[o + i] * (1 - alpha) + color[i] * alpha)


def fill_rect(buf, x0, y0, x1, y1, color, alpha=1.0):
    for y in range(int(y0), int(y1)):
        for x in range(int(x0), int(x1)):
            put(buf, x, y, color, alpha)


def stroke_rect(buf, x0, y0, x1, y1, color, sw):
    t = max(1, int(sw * SCALE))
    fill_rect(buf, x0, y0, x1, y0 + t, color)
    fill_rect(buf, x0, y1 - t, x1, y1, color)
    fill_rect(buf, x0, y0, x0 + t, y1, color)
    fill_rect(buf, x1 - t, y0, x1, y1, color)


def line(buf, x0, y0, x1, y1, color, sw):
    t = max(1, int(round(sw * SCALE)))
    dx, dy = x1 - x0, y1 - y0
    steps = int(max(abs(dx), abs(dy))) + 1
    for i in range(steps + 1):
        px = x0 + dx * i / steps
        py = y0 + dy * i / steps
        fill_rect(buf, px - t / 2.0, py - t / 2.0, px + t / 2.0, py + t / 2.0, color)


def disc(buf, cx, cy, r, color, alpha=1.0):
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                put(buf, x, y, color, alpha)


def ring(buf, cx, cy, r, color, sw):
    inner = max(0.0, r - sw * SCALE / 2.0)
    outer = r + sw * SCALE / 2.0
    for y in range(int(cy - outer), int(cy + outer) + 1):
        for x in range(int(cx - outer), int(cx + outer) + 1):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if inner <= d <= outer:
                put(buf, x, y, color)


def poly(buf, points, color):
    if len(points) < 3:
        return
    ys = [p[1] for p in points]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        xs = []
        for i in range(len(points)):
            ax, ay = points[i]
            bx, by = points[(i + 1) % len(points)]
            if ay == by:
                continue
            if min(ay, by) <= y <= max(ay, by):
                xs.append(ax + (y - ay) * (bx - ax) / (by - ay))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            fill_rect(buf, xs[i], y, xs[i + 1] + 1, y + 1, color)


def rgb(color):
    return (int(color[0] * 255), int(color[1] * 255), int(color[2] * 255))


def render(page):
    buf = blank()
    s = SCALE
    for item in page.rec:
        kind = item[0]
        if kind == "rect":
            _, x, y, w, h, fill, stroke, sw, radius = item
            x0, y0, x1, y1 = x * s, y * s, (x + w) * s, (y + h) * s
            if fill:
                if radius > 0:
                    # approximate: fill then clip corners
                    fill_rect(buf, x0 + radius * s * 0.4, y0, x1 - radius * s * 0.4, y1, rgb(fill))
                    fill_rect(buf, x0, y0 + radius * s * 0.4, x1, y1 - radius * s * 0.4, rgb(fill))
                else:
                    fill_rect(buf, x0, y0, x1, y1, rgb(fill))
            if stroke:
                stroke_rect(buf, x0, y0, x1, y1, rgb(stroke), sw)
        elif kind == "line":
            _, x1, y1, x2, y2, color, width = item
            line(buf, x1 * s, y1 * s, x2 * s, y2 * s, rgb(color), width)
        elif kind == "circle":
            _, cx, cy, r, fill, stroke, sw = item
            if fill:
                disc(buf, cx * s, cy * s, r * s, rgb(fill))
            if stroke:
                ring(buf, cx * s, cy * s, r * s, rgb(stroke), sw)
        elif kind == "arc":
            _, cx, cy, r, a0, a1, color, width = item
            steps = 48
            prev = None
            for i in range(steps + 1):
                ang = (a0 + (a1 - a0) * i / steps) * 3.14159265 / 180.0
                px = (cx + r * __import__("math").cos(ang)) * s
                py = (cy + r * __import__("math").sin(ang)) * s
                if prev:
                    line(buf, prev[0], prev[1], px, py, rgb(color), width)
                prev = (px, py)
        elif kind == "poly":
            _, points, fill, stroke, sw = item
            pts = [(p[0] * s, p[1] * s) for p in points]
            if fill:
                poly(buf, pts, rgb(fill))
            if stroke:
                for i in range(len(pts)):
                    a = pts[i]
                    b = pts[(i + 1) % len(pts)]
                    line(buf, a[0], a[1], b[0], b[1], rgb(stroke), sw)
        elif kind == "text":
            _, x, y, text, size, bold, color = item
            width = M.text_width(text, size, bold)
            if not text.strip():
                continue
            # bar standing in for the glyph run
            top = (y + size * 0.16) * s
            bottom = (y + size * 0.92) * s
            alpha = 0.75 if bold else 0.42
            fill_rect(buf, x * s, top, (x + width) * s, bottom, rgb(color), alpha)
        elif kind == "image":
            _, x, y, w, h, name = item
            fill_rect(buf, x * s, y * s, (x + w) * s, (y + h) * s, (79, 70, 229))
    return buf


def downsample(buf):
    out = bytearray(PW // 2 * PH // 2 * 3)
    ow = PW // 2
    for y in range(PH // 2):
        for x in range(ow):
            acc = [0, 0, 0]
            for dy in range(2):
                for dx in range(2):
                    o = ((y * 2 + dy) * PW + (x * 2 + dx)) * 3
                    acc[0] += buf[o]
                    acc[1] += buf[o + 1]
                    acc[2] += buf[o + 2]
            o = (y * ow + x) * 3
            out[o] = acc[0] // 4
            out[o + 1] = acc[1] // 4
            out[o + 2] = acc[2] // 4
    return out, ow, PH // 2


def write_png(path, width, height, rgb_data):
    raw = bytearray()
    stride = width * 3
    for y in range(height):
        raw.append(0)
        raw.extend(rgb_data[y * stride:(y + 1) * stride])

    def chunk(tag, payload):
        out = struct.pack(">I", len(payload)) + tag + payload
        return out + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
           + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    outdir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.expanduser("~/dev"), "manual-preview")
    os.makedirs(outdir, exist_ok=True)

    doc = M.build_doc()

    total = len(doc.pages)
    for index, page in enumerate(doc.pages, 1):
        page.footer(total)
        small, width, height = downsample(render(page))
        path = os.path.join(outdir, "page-%02d.png" % index)
        write_png(path, width, height, small)
        print("page %d -> %s" % (index, path))


if __name__ == "__main__":
    main()
