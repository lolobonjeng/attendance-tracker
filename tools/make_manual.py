#!/usr/bin/env python3
"""Builds the AttendanceTracker user manual as a PDF.

No third-party dependencies: the PDF container, the text layout engine and all
diagrams are produced here. Diagrams are vector graphics drawn straight into the
page content stream, so they stay sharp at any zoom.

Usage:
    python3 tools/make_manual.py [output.pdf]
"""

import math
import os
import struct
import sys
import zlib

# ------------------------------------------------------------------ geometry

W, H = 595.276, 841.890          # A4 portrait in points
MARGIN_L = 54.0
MARGIN_R = 54.0
MARGIN_T = 54.0
MARGIN_B = 54.0
CONTENT_W = W - MARGIN_L - MARGIN_R
BOTTOM_LIMIT = H - MARGIN_B

# ------------------------------------------------------------------- palette

INK = (0.067, 0.094, 0.153)
INK_SOFT = (0.239, 0.275, 0.337)
MUTED = (0.420, 0.447, 0.502)
FAINT = (0.612, 0.639, 0.686)
PRIMARY = (0.310, 0.275, 0.898)
PRIMARY_DK = (0.239, 0.208, 0.780)
ACCENT = (0.020, 0.549, 0.678)
SUCCESS = (0.020, 0.588, 0.412)
WARN = (0.851, 0.467, 0.023)
DANGER = (0.863, 0.149, 0.149)
RULE = (0.898, 0.906, 0.925)
SOFT = (0.965, 0.972, 0.980)
SOFT_2 = (0.937, 0.949, 0.965)
WHITE = (1.0, 1.0, 1.0)
COVER_BG = (0.043, 0.063, 0.125)
COVER_SOFT = (0.078, 0.106, 0.192)

# --------------------------------------------------------------- text metrics
# Helvetica advance widths (units per 1000 em) for the printable ASCII range.
_WIDTHS = {
    " ": 278, "!": 278, '"': 355, "#": 556, "$": 556, "%": 889, "&": 667,
    "'": 191, "(": 333, ")": 333, "*": 389, "+": 584, ",": 278, "-": 333,
    ".": 278, "/": 278, ":": 278, ";": 278, "<": 584, "=": 584, ">": 584,
    "?": 556, "@": 1015, "[": 278, "\\": 278, "]": 278, "^": 469, "_": 556,
    "`": 333, "{": 334, "|": 260, "}": 334, "~": 584,
}
for _c in "0123456789":
    _WIDTHS[_c] = 556
for _c, _w in zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                  [667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556,
                   833, 722, 778, 667, 778, 722, 667, 611, 722, 667, 944, 667,
                   667, 611]):
    _WIDTHS[_c] = _w
for _c, _w in zip("abcdefghijklmnopqrstuvwxyz",
                  [556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222,
                   833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500,
                   500, 500]):
    _WIDTHS[_c] = _w
for _c in "\u2013\u2014\u2018\u2019\u201c\u201d\u2026\u00b7\u2022":
    _WIDTHS[_c] = 556

# WinAnsi has no peso sign; the manual spells the currency out instead.
_SUBS = {
    "\u20b1": "PHP ",
    "\u2192": "->",
    "\u2190": "<-",
    "\u2264": "<=",
    "\u2265": ">=",
    "\u2713": "OK",
}


def _clean(text):
    out = []
    for ch in text:
        out.append(_SUBS.get(ch, ch))
    return "".join(out)


def text_width(text, size, bold=False):
    total = 0
    for ch in _clean(text):
        total += _WIDTHS.get(ch, 556)
    w = total * size / 1000.0
    return w * 1.045 if bold else w


def esc(text):
    """Encodes to WinAnsi and escapes the three special PDF characters."""
    out = bytearray()
    for ch in _clean(text):
        try:
            data = ch.encode("cp1252")
        except UnicodeEncodeError:
            data = b"?"
        if data in (b"(", b")", b"\\"):
            out += b"\\" + data
        elif len(data) == 1 and data[0] < 32:
            out += b" "
        else:
            out += data
    return bytes(out)


def wrap(text, size, width, bold=False):
    """Greedy word wrap using real Helvetica metrics."""
    words = text.split()
    if not words:
        return [""]
    lines = []
    line = words[0]
    for word in words[1:]:
        trial = line + " " + word
        if text_width(trial, size, bold) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    lines.append(line)

    # Break any single word that is wider than the column.
    out = []
    for line in lines:
        if text_width(line, size, bold) <= width:
            out.append(line)
            continue
        chunk = ""
        for ch in line:
            if text_width(chunk + ch, size, bold) > width and chunk:
                out.append(chunk)
                chunk = ch
            else:
                chunk += ch
        if chunk:
            out.append(chunk)
    return out


# --------------------------------------------------------------------- pdf io

class Pdf(object):
    def __init__(self):
        self.bodies = {}
        self.order = []
        self.next_num = 1

    def reserve(self):
        num = self.next_num
        self.next_num += 1
        self.bodies[num] = None
        self.order.append(num)
        return num

    def put(self, num, body):
        self.bodies[num] = body

    def add(self, body):
        num = self.reserve()
        self.put(num, body)
        return num

    def add_stream(self, data, extra=""):
        head = ("<< /Length %d%s >>\nstream\n" % (len(data), extra)).encode("latin-1")
        return self.add(head + data + b"\nendstream")

    def write(self, path):
        out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        offsets = {}
        for num in self.order:
            body = self.bodies[num]
            if body is None:
                raise RuntimeError("object %d was reserved but never filled" % num)
            offsets[num] = len(out)
            out += ("%d 0 obj\n" % num).encode("latin-1")
            out += body
            out += b"\nendobj\n"

        xref_at = len(out)
        count = self.next_num
        out += ("xref\n0 %d\n" % count).encode("latin-1")
        out += b"0000000000 65535 f \n"
        for num in range(1, count):
            out += ("%010d 00000 n \n" % offsets.get(num, 0)).encode("latin-1")
        out += ("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"
                % (count, xref_at)).encode("latin-1")

        with open(path, "wb") as handle:
            handle.write(out)
        return len(out)


# ----------------------------------------------------------------------- page

class Page(object):
    def __init__(self, doc, number, dark=False):
        self.doc = doc
        self.number = number
        self.ops = []
        self.rec = []          # display list, used by tools/preview_manual.py
        self.y = MARGIN_T
        self.dark = dark
        if dark:
            self.rect(0, 0, W, H, fill=COVER_BG)

    # -- primitives ------------------------------------------------------

    def _color(self, rgb, stroke=False):
        return ("%.3f %.3f %.3f %s" % (rgb[0], rgb[1], rgb[2],
                                       "RG" if stroke else "rg"))

    def rect(self, x, y, w, h, fill=None, stroke=None, sw=0.7, radius=0.0):
        self.rec.append(("rect", x, y, w, h, fill, stroke, sw, radius))
        yb = H - y - h
        self.ops.append("q")
        if radius > 0:
            self._round_path(x, yb, w, h, min(radius, w / 2.0, h / 2.0))
        else:
            self.ops.append("%.3f %.3f %.3f %.3f re" % (x, yb, w, h))
        if fill is not None:
            self.ops.append(self._color(fill) + " f")
        if stroke is not None:
            self.ops.append("%.3f w" % sw)
            self.ops.append(self._color(stroke, True) + " S")
        self.ops.append("Q")

    def _round_path(self, x, yb, w, h, r):
        k = 0.5522847498 * r
        self.ops.append("%.3f %.3f m" % (x + r, yb + h))
        self.ops.append("%.3f %.3f l" % (x + w - r, yb + h))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (x + w - r + k, yb + h, x + w, yb + h - r + k, x + w, yb + h - r))
        self.ops.append("%.3f %.3f l" % (x + w, yb + r))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (x + w, yb + r - k, x + w - r + k, yb, x + w - r, yb))
        self.ops.append("%.3f %.3f l" % (x + r, yb))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (x + r - k, yb, x, yb + r - k, x, yb + r))
        self.ops.append("%.3f %.3f l" % (x, yb + h - r))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (x, yb + h - r + k, x + r - k, yb + h, x + r, yb + h))
        self.ops.append("h")

    def line(self, x1, y1, x2, y2, color=RULE, width=0.7, dash=None):
        self.rec.append(("line", x1, y1, x2, y2, color, width))
        self.ops.append("q")
        self.ops.append("%.3f w" % width)
        self.ops.append(self._color(color, True))
        if dash:
            self.ops.append("[%.2f %.2f] 0 d" % dash)
        self.ops.append("%.3f %.3f m %.3f %.3f l S" % (x1, H - y1, x2, H - y2))
        self.ops.append("Q")

    def circle(self, cx, cy, r, fill=None, stroke=None, sw=0.8):
        self.rec.append(("circle", cx, cy, r, fill, stroke, sw))
        self.ops.append("q")
        k = 0.5522847498 * r
        y = H - cy
        self.ops.append("%.3f %.3f m" % (cx + r, y))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (cx + r, y + k, cx + k, y + r, cx, y + r))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (cx - k, y + r, cx - r, y + k, cx - r, y))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (cx - r, y - k, cx - k, y - r, cx, y - r))
        self.ops.append("%.3f %.3f %.3f %.3f %.3f %.3f c"
                        % (cx + k, y - r, cx + r, y - k, cx + r, y))
        self.ops.append("h")
        if fill is not None:
            self.ops.append(self._color(fill) + " f")
        if stroke is not None:
            self.ops.append("%.3f w" % sw)
            self.ops.append(self._color(stroke, True) + " S")
        self.ops.append("Q")

    def arc(self, cx, cy, r, start_deg, end_deg, color=PRIMARY, width=3.0):
        """Stroke an arc clockwise from start to end (degrees, screen space)."""
        self.rec.append(("arc", cx, cy, r, start_deg, end_deg, color, width))
        self.ops.append("q")
        self.ops.append("%.3f w" % width)
        self.ops.append("1 J 1 j")
        self.ops.append(self._color(color, True))
        sweep = end_deg - start_deg
        steps = max(2, int(abs(sweep) / 4) + 1)
        for i in range(steps + 1):
            ang = math.radians(start_deg + sweep * i / float(steps))
            px = cx + r * math.cos(ang)
            py = cy + r * math.sin(ang)
            self.ops.append("%.3f %.3f %s" % (px, H - py, "m" if i == 0 else "l"))
        self.ops.append("S")
        self.ops.append("Q")

    def polygon(self, points, fill=None, stroke=None, sw=0.7):
        if not points:
            return
        self.rec.append(("poly", list(points), fill, stroke, sw))
        self.ops.append("q")
        self.ops.append("%.3f %.3f m" % (points[0][0], H - points[0][1]))
        for px, py in points[1:]:
            self.ops.append("%.3f %.3f l" % (px, H - py))
        self.ops.append("h")
        if fill is not None:
            self.ops.append(self._color(fill) + " f")
        if stroke is not None:
            self.ops.append("%.3f w" % sw)
            self.ops.append(self._color(stroke, True) + " S")
        self.ops.append("Q")

    def arrow(self, x1, y1, x2, y2, color=FAINT, width=1.0, head=5.0, dash=None):
        self.line(x1, y1, x2, y2, color, width, dash)
        dx, dy = x2 - x1, y2 - y1
        length = math.hypot(dx, dy)
        if length < 0.01:
            return
        ux, uy = dx / length, dy / length
        px, py = -uy, ux
        base_x, base_y = x2 - ux * head, y2 - uy * head
        half = head * 0.42
        self.polygon([(x2, y2),
                      (base_x + px * half, base_y + py * half),
                      (base_x - px * half, base_y - py * half)], fill=color)

    def image(self, x, y, w, h, name):
        self.rec.append(("image", x, y, w, h, name))
        yb = H - y - h
        self.ops.append("q %.3f 0 0 %.3f %.3f %.3f cm /%s Do Q"
                        % (w, h, x, yb, name))

    # -- text ------------------------------------------------------------

    def text(self, x, y, s, size=9.8, bold=False, italic=False, color=INK,
             align="left", width=None):
        if s is None or s == "":
            return
        font = "F2" if bold else ("F3" if italic else "F1")
        if align != "left" and width is not None:
            tw = text_width(s, size, bold)
            if align == "center":
                x = x + (width - tw) / 2.0
            elif align == "right":
                x = x + width - tw
        baseline = H - (y + size * 0.76)
        self.rec.append(("text", x, y, s, size, bold, color))
        self.ops.append("%s" % self._color(color))
        self.ops.append("BT /%s %.2f Tf 1 0 0 1 %.3f %.3f Tm (%s) Tj ET"
                        % (font, size, x, baseline, esc(s).decode("latin-1")))

    # -- flow ------------------------------------------------------------

    def space(self, amount):
        self.y += amount

    def h1(self, kicker, title):
        if kicker:
            self.text(MARGIN_L, self.y, kicker.upper(), 7.6, True, color=ACCENT)
            self.y += 12
        self.text(MARGIN_L, self.y, title, 19.5, True, color=INK)
        self.y += 27
        self.rect(MARGIN_L, self.y, 34, 2.6, fill=PRIMARY, radius=1.3)
        self.y += 16

    def h2(self, title, gap=15):
        self.y += gap
        self.text(MARGIN_L, self.y, title, 12.2, True, color=INK)
        self.y += 18

    def h3(self, title):
        self.text(MARGIN_L, self.y, title, 9.9, True, color=PRIMARY_DK)
        self.y += 14

    def para(self, body, size=9.5, color=INK_SOFT, leading=14.2, gap=7, x=None,
             width=None):
        x = MARGIN_L if x is None else x
        width = CONTENT_W if width is None else width
        for line in wrap(body, size, width):
            self.text(x, self.y, line, size, color=color)
            self.y += leading
        self.y += gap

    def bullets(self, items, size=9.4, leading=13.8, gap=8, marker="\u2022"):
        for item in items:
            self.text(MARGIN_L + 2, self.y, marker, size, True, color=PRIMARY)
            lines = wrap(item, size, CONTENT_W - 16)
            for i, line in enumerate(lines):
                self.text(MARGIN_L + 14, self.y, line, size, color=INK_SOFT)
                self.y += leading
            self.y += 1.6
        self.y += gap

    def note(self, title, body, color=ACCENT, tint=SOFT):
        lines = wrap(body, 8.9, CONTENT_W - 30)
        box_h = 16 + len(lines) * 12.6 + 8
        self.rect(MARGIN_L, self.y, CONTENT_W, box_h, fill=tint, radius=5)
        self.rect(MARGIN_L, self.y, 2.6, box_h, fill=color, radius=1.3)
        yy = self.y + 9
        self.text(MARGIN_L + 13, yy, title, 8.8, True, color=color)
        yy += 13
        for line in lines:
            self.text(MARGIN_L + 13, yy, line, 8.9, color=INK_SOFT)
            yy += 12.6
        self.y += box_h + 11

    def code(self, lines, size=8.2, tint=(0.075, 0.098, 0.169)):
        leading = 12.2
        box_h = 12 + len(lines) * leading + 6
        self.rect(MARGIN_L, self.y, CONTENT_W, box_h, fill=tint, radius=5)
        yy = self.y + 12
        for line in lines:
            self.text(MARGIN_L + 12, yy, line, size, color=(0.78, 0.85, 0.94))
            yy += leading
        self.y += box_h + 11

    def table(self, header, rows, widths, size=8.5, leading=11.4, pad=6,
              head_fill=SOFT_2):
        total = sum(widths)
        scale = CONTENT_W / total
        widths = [w * scale for w in widths]

        def draw_row(cells, y, is_head):
            height = 0
            wrapped = []
            for i, cell in enumerate(cells):
                lines = wrap(str(cell), size, widths[i] - pad * 2, is_head)
                wrapped.append(lines)
                height = max(height, len(lines) * leading)
            row_h = height + pad * 2
            if is_head:
                self.rect(MARGIN_L, y, CONTENT_W, row_h, fill=head_fill, radius=3)
            x = MARGIN_L
            for i, lines in enumerate(wrapped):
                color = INK if is_head else INK_SOFT
                yy = y + pad + leading - 2.6
                for line in lines:
                    self.text(x + pad, yy, line, size, is_head, color=color)
                    yy += leading
                x += widths[i]
            return row_h

        head_h = draw_row(header, self.y, True)
        self.y += head_h
        for index, row in enumerate(rows):
            row_h = draw_row(row, self.y, False)
            if index < len(rows) - 1:
                self.line(MARGIN_L, self.y + row_h, MARGIN_L + CONTENT_W,
                          self.y + row_h, RULE, 0.6)
            self.y += row_h
        self.y += 10

    def footer(self, total):
        """Running footer. Drawn outside the text flow, so it never shifts content."""
        if self.number == 1:
            return
        fy = H - 44
        self.line(MARGIN_L, fy, MARGIN_L + CONTENT_W, fy, RULE, 0.6)
        self.text(MARGIN_L, fy + 8, "Attendance Tracker  \u00b7  User Manual",
                  7.2, color=FAINT)
        self.text(MARGIN_L, fy + 8, "Page %d of %d" % (self.number, total),
                  7.2, color=FAINT, align="right", width=CONTENT_W)

    def end(self):
        if self.y > BOTTOM_LIMIT + 1:
            raise RuntimeError("page %d overflows by %.1f pt"
                               % (self.number, self.y - BOTTOM_LIMIT))


# ------------------------------------------------------------------ document

class Doc(object):
    def __init__(self):
        self.pdf = Pdf()
        self.catalog = self.pdf.reserve()
        self.pages_root = self.pdf.reserve()
        self.f1 = self.pdf.add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                               b"/Encoding /WinAnsiEncoding >>")
        self.f2 = self.pdf.add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold "
                               b"/Encoding /WinAnsiEncoding >>")
        self.f3 = self.pdf.add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Oblique "
                               b"/Encoding /WinAnsiEncoding >>")
        self.pages = []
        self.images = {}

    def new_page(self, dark=False):
        page = Page(self, len(self.pages) + 1, dark=dark)
        self.pages.append(page)
        return page

    def add_image(self, name, path, bg=(255, 255, 255)):
        width, height, rgb = read_png_rgb(path, bg)
        comp = zlib.compress(rgb, 9)
        head = ("<< /Type /XObject /Subtype /Image /Width %d /Height %d "
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode "
                "/Length %d >>\nstream\n" % (width, height, len(comp)))
        num = self.pdf.add(head.encode("latin-1") + comp + b"\nendstream")
        self.images[name] = num
        return width, height

    def finish(self, path):
        xobjects = " ".join("/%s %d 0 R" % (k, v) for k, v in self.images.items())
        resources = ("<< /Font << /F1 %d 0 R /F2 %d 0 R /F3 %d 0 R >>"
                     % (self.f1, self.f2, self.f3))
        if xobjects:
            resources += " /XObject << %s >>" % xobjects
        resources += " >>"

        kids = []
        total = len(self.pages)
        for page in self.pages:
            page.footer(total)
            content = self.pdf.add_stream(("\n".join(page.ops)).encode("latin-1"))
            body = ("<< /Type /Page /Parent %d 0 R /MediaBox [0 0 %.3f %.3f] "
                    "/Resources %s /Contents %d 0 R >>"
                    % (self.pages_root, W, H, resources, content))
            num = self.pdf.add(body.encode("latin-1"))
            kids.append(num)
            page.end()

        self.pdf.put(self.pages_root,
                     ("<< /Type /Pages /Kids [%s] /Count %d >>"
                      % (" ".join("%d 0 R" % k for k in kids), len(kids))).encode("latin-1"))
        self.pdf.put(self.catalog,
                     ("<< /Type /Catalog /Pages %d 0 R >>" % self.pages_root).encode("latin-1"))
        return self.pdf.write(path)


def read_png_rgb(path, bg=(255, 255, 255)):
    """Decodes the RGBA PNGs produced by tools/make_logo.py (filter 0 rows)."""
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG: %s" % path)
    pos = 8
    width = height = depth = ctype = None
    idat = bytearray()
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype = struct.unpack(">IIBB", chunk[:10])
        elif tag == b"IDAT":
            idat += chunk
        elif tag == b"IEND":
            break
    if depth != 8 or ctype != 6:
        raise ValueError("expected 8-bit RGBA, got depth=%s type=%s" % (depth, ctype))

    raw = zlib.decompress(bytes(idat))
    stride = width * 4
    out = bytearray(width * height * 3)
    for y in range(height):
        start = y * (stride + 1)
        if raw[start] != 0:
            raise ValueError("unsupported PNG filter %d on row %d" % (raw[start], y))
        row = raw[start + 1:start + 1 + stride]
        for x in range(width):
            r, g, b, a = row[x * 4:x * 4 + 4]
            if a != 255:
                r = (r * a + bg[0] * (255 - a)) // 255
                g = (g * a + bg[1] * (255 - a)) // 255
                b = (b * a + bg[2] * (255 - a)) // 255
            o = (y * width + x) * 3
            out[o] = r
            out[o + 1] = g
            out[o + 2] = b
    return width, height, bytes(out)


# ------------------------------------------------------------------- diagram

def box(page, x, y, w, h, title, sub=None, fill=WHITE, stroke=RULE,
        color=INK, size=8.4, radius=5, bold=True):
    page.rect(x, y, w, h, fill=fill, stroke=stroke, sw=0.8, radius=radius)
    if sub:
        page.text(x, y + h / 2 - 8.4, title, size, bold, color=color,
                  align="center", width=w)
        page.text(x, y + h / 2 + 2.4, sub, size - 1.6, False, color=MUTED,
                  align="center", width=w)
    else:
        page.text(x, y, title, size, bold, color=color, align="center",
                  width=w)
        page.y = y + h / 2 - size * 0.42


def centered_box(page, cx, y, w, h, title, sub=None, **kw):
    return box(page, cx - w / 2.0, y, w, h, title, sub, **kw)


def caption(page, x, y, w, text):
    page.text(x, y, text, 7.8, color=MUTED, align="center", width=w)


def day_flow(page, x, y, w):
    """The four things that happen on a normal working day."""
    gap = 24
    bw = (w - gap * 3) / 4.0
    h = 52
    steps = [("1", "Check in", "You start work"),
             ("2", "Break", "You step away"),
             ("3", "Check out", "You go home"),
             ("4", "See the total", "Hours are added up")]
    for i, (num, title, sub) in enumerate(steps):
        bx = x + i * (bw + gap)
        page.rect(bx, y, bw, h, fill=WHITE, stroke=RULE, sw=0.8, radius=6)
        page.circle(bx + 13, y + 14, 7.2, fill=PRIMARY)
        page.text(bx + 5.8, y + 11.2, num, 7.0, True, color=WHITE,
                  align="center", width=14)
        page.text(bx + 6, y + 26, title, 8.8, True, color=INK)
        page.text(bx + 6, y + 37, sub, 7.4, color=MUTED)
        if i < 3:
            page.arrow(bx + bw + 3, y + h / 2, bx + bw + gap - 3, y + h / 2,
                       color=FAINT, width=1.0)
    caption(page, x, y + h + 6, w, "One tap per event \u2014 the app does the arithmetic")
    return h + 22


def punch_timeline(page, x, y, w):
    """The six punch slots drawn as a day timeline."""
    slots = [("In 1", SUCCESS), ("Out 1", WARN), ("In 2", SUCCESS),
             ("Out 2", WARN), ("In 3", SUCCESS), ("Out 3", WARN)]
    n = len(slots)
    step = (w - 34) / (n - 1)
    axis_y = y + 26
    page.line(x + 16, axis_y, x + w - 16, axis_y, RULE, 1.4)
    for i, (label, color) in enumerate(slots):
        cx = x + 16 + i * step
        page.circle(cx, axis_y, 7.5, fill=WHITE, stroke=color, sw=1.6)
        page.circle(cx, axis_y, 3.2, fill=color)
        page.text(cx - 26, axis_y + 13, label, 7.6, True, color=INK,
                  align="center", width=52)
        page.text(cx - 26, axis_y - 24, "slot %d" % (i // 2 + 1), 6.8,
                  color=FAINT, align="center", width=52)
    page.text(x, axis_y - 42, "Working time is the sum of the gaps between an In and the next Out.",
              7.6, color=MUTED)
    return 78


def phone_frame(page, x, y, w, h, title):
    page.rect(x, y, w, h, fill=(0.043, 0.063, 0.125), radius=14)
    page.rect(x + 2.4, y + 2.4, w - 4.8, h - 4.8, fill=(0.043, 0.063, 0.125), radius=12)
    page.text(x, y - 11, title, 7.6, True, color=MUTED, align="center", width=w)
    return x + 2.4, y + 2.4, w - 4.8, h - 4.8


def bar(page, x, y, w, h, color=(0.35, 0.40, 0.50), radius=None):
    page.rect(x, y, w, h, fill=color, radius=h / 2.0 if radius is None else radius)


def dash_screen(page, x, y, w, h):
    ix, iy, iw, ih = phone_frame(page, x, y, w, h, "Dashboard")
    pad = 8
    # status bar
    page.text(ix + pad, iy + 6, "9:41", 5.2, True, color=(0.7, 0.75, 0.85))
    bar(page, ix + iw - pad - 16, iy + 8, 16, 3.2, (0.55, 0.6, 0.7))
    # header
    page.rect(ix + pad, iy + 15, 11, 11, fill=PRIMARY, radius=3)
    bar(page, ix + pad + 15, iy + 17, 38, 4.4, (0.85, 0.88, 0.94))
    bar(page, ix + pad + 15, iy + 24, 26, 3.2, (0.45, 0.5, 0.6))
    page.circle(ix + iw - pad - 5.5, iy + 21, 5.5, fill=(0.13, 0.17, 0.26))
    # employee chip
    cy = iy + 34
    page.rect(ix + pad, cy, iw - pad * 2, 18, fill=(0.1, 0.13, 0.21), radius=9)
    page.circle(ix + pad + 9, cy + 9, 6, fill=(0.2, 0.24, 0.35))
    bar(page, ix + pad + 19, cy + 6, 40, 3.6, (0.8, 0.84, 0.92))
    bar(page, ix + pad + 19, cy + 12, 24, 2.8, (0.45, 0.5, 0.62))
    # hero card
    hy = cy + 24
    hh = 108
    page.rect(ix + pad, hy, iw - pad * 2, hh, fill=(0.086, 0.11, 0.19), radius=9)
    ccx = ix + iw / 2.0
    ccy = hy + 44
    page.circle(ccx, ccy, 28, stroke=(0.16, 0.2, 0.3), sw=3.4)
    page.arc(ccx, ccy, 28, -90, 90, color=(0.39, 0.4, 0.95), width=3.4)
    page.text(ccx - 30, ccy - 9, "4:12", 11, True, color=WHITE, align="center", width=60)
    page.text(ccx - 30, ccy + 3, "of 8:00", 5.4, color=(0.55, 0.6, 0.72),
              align="center", width=60)
    page.rect(ccx - 21, hy + 80, 42, 9, fill=(0.13, 0.17, 0.26), radius=4.5)
    page.text(ccx - 21, hy + 82, "WORKING", 4.8, True, color=(0.2, 0.83, 0.6),
              align="center", width=42)
    # primary button
    by = hy + hh + 7
    page.rect(ix + pad, by, iw - pad * 2, 19, fill=(0.39, 0.36, 0.93), radius=9.5)
    page.text(ix + pad, by + 5.5, "Check out", 6.6, True, color=WHITE,
              align="center", width=iw - pad * 2)
    # stats 2x2
    sy = by + 24
    cw = (iw - pad * 2 - 5) / 2.0
    stats = [("Worked", "4:12"), ("Break", "0:40"), ("Overtime", "--"), ("Late", "--")]
    for i, (label, value) in enumerate(stats):
        bx = ix + pad + (i % 2) * (cw + 5)
        byy = sy + (i // 2) * 22
        page.rect(bx, byy, cw, 18, fill=(0.09, 0.12, 0.2), radius=5)
        page.text(bx + 4, byy + 3, label, 4.6, color=(0.5, 0.55, 0.67))
        page.text(bx + 4, byy + 9, value, 6.2, True, color=(0.9, 0.92, 0.96))
    # timeline
    ty = sy + 50
    for i in range(3):
        yy = ty + i * 12
        page.circle(ix + pad + 5, yy + 3, 2.4, fill=[SUCCESS, WARN, (0.35, 0.4, 0.5)][i])
        bar(page, ix + pad + 12, yy + 1.5, 30, 3.2, (0.55, 0.6, 0.72))
        bar(page, ix + pad + 48, yy + 1.5, 16, 3.2, (0.35, 0.4, 0.52))
    # bottom nav
    nav_y = iy + ih - 20
    page.rect(ix, nav_y, iw, 20, fill=(0.06, 0.08, 0.15), radius=0)
    for i in range(4):
        cx = ix + iw * (i + 0.5) / 4.0
        active = i == 0
        page.rect(cx - 9, nav_y + 3, 18, 14, fill=(0.16, 0.18, 0.32) if active else None,
                  radius=7)
        page.circle(cx, nav_y + 8, 2.6, fill=(0.4, 0.42, 0.98) if active else (0.4, 0.45, 0.57))
        bar(page, cx - 5, nav_y + 13, 10, 1.8,
            (0.4, 0.42, 0.98) if active else (0.4, 0.45, 0.57))


def history_screen(page, x, y, w, h):
    ix, iy, iw, ih = phone_frame(page, x, y, w, h, "History")
    pad = 8
    page.text(ix + pad, iy + 8, "October 2026", 7.4, True, color=(0.9, 0.92, 0.96))
    page.circle(ix + iw - pad - 5, iy + 12, 5, fill=(0.13, 0.17, 0.26))
    page.circle(ix + iw - pad - 17, iy + 12, 5, fill=(0.13, 0.17, 0.26))
    # summary
    page.rect(ix + pad, iy + 22, iw - pad * 2, 30, fill=(0.086, 0.11, 0.19), radius=7)
    for i, (lab, val) in enumerate([("21", "days"), ("162h", "worked"), ("3", "late")]):
        cx = ix + pad + (iw - pad * 2) * (i + 0.5) / 3.0
        page.text(cx - 20, iy + 27, val, 7.4, True, color=(0.9, 0.92, 0.96),
                  align="center", width=40)
        page.text(cx - 20, iy + 37, lab, 4.8, color=(0.5, 0.55, 0.67),
                  align="center", width=40)
    # rows
    ry = iy + 58
    dates = ["26", "25", "24", "23", "22", "21", "20", "19"]
    dows = ["M", "S", "F", "T", "W", "T", "M", "S"]
    for i in range(8):
        yy = ry + i * 28
        page.rect(ix + pad, yy, iw - pad * 2, 24, fill=(0.09, 0.12, 0.2), radius=5)
        page.rect(ix + pad + 4, yy + 5, 14, 14, fill=(0.14, 0.17, 0.27), radius=4)
        page.text(ix + pad + 4, yy + 7.4, dates[i], 5.6,
                  True, color=(0.75, 0.8, 0.9), align="center", width=14)
        page.text(ix + pad + 4, yy + 14.4, dows[i], 4.2,
                  color=(0.45, 0.5, 0.62), align="center", width=14)
        bar(page, ix + pad + 22, yy + 5, 34, 3.4, (0.8, 0.84, 0.92))
        bar(page, ix + pad + 22, yy + 12, 22, 2.8, (0.42, 0.47, 0.58))
        page.rect(ix + iw - pad - 34, yy + 5, 30, 8, fill=(0.1, 0.28, 0.24), radius=4)
        page.text(ix + iw - pad - 34, yy + 6.6, "8:12", 4.8, True,
                  color=(0.2, 0.83, 0.6), align="center", width=30)


def team_screen(page, x, y, w, h):
    ix, iy, iw, ih = phone_frame(page, x, y, w, h, "Team")
    pad = 8
    page.text(ix + pad, iy + 8, "Team", 8.2, True, color=(0.9, 0.92, 0.96))
    page.text(ix + pad, iy + 17, "3 active", 5, color=(0.5, 0.55, 0.67))
    for i in range(7):
        yy = iy + 28 + i * 30
        page.rect(ix + pad, yy, iw - pad * 2, 25, fill=(0.09, 0.12, 0.2), radius=6)
        page.circle(ix + pad + 12, yy + 12.5, 8, fill=(0.19, 0.2, 0.4))
        page.text(ix + pad + 2, yy + 9.6, ["AR", "JD", "MC", "LP", "RS", "TN", "VO"][i],
                  5.2, True, color=(0.65, 0.66, 0.98), align="center", width=20)
        bar(page, ix + pad + 24, yy + 7, 40, 3.6, (0.82, 0.86, 0.93))
        bar(page, ix + pad + 24, yy + 14, 26, 2.8, (0.42, 0.47, 0.58))
        page.rect(ix + iw - pad - 26, yy + 8, 22, 8, fill=(0.12, 0.18, 0.3), radius=4)
        page.text(ix + iw - pad - 26, yy + 9.6, "9-6", 4.4, color=(0.55, 0.62, 0.78),
                  align="center", width=22)
    page.circle(ix + iw - 16, iy + ih - 18, 10, fill=(0.39, 0.36, 0.93))
    page.text(ix + iw - 21, iy + ih - 21, "+", 9, True, color=WHITE,
              align="center", width=10)


def reports_screen(page, x, y, w, h):
    ix, iy, iw, ih = phone_frame(page, x, y, w, h, "Reports")
    pad = 8
    page.text(ix + pad, iy + 8, "Reports", 8.2, True, color=(0.9, 0.92, 0.96))
    page.text(ix + pad, iy + 17, "Oct 1 - Oct 31", 4.8, color=(0.5, 0.55, 0.67))
    # segments
    sy = iy + 25
    page.rect(ix + pad, sy, iw - pad * 2, 13, fill=(0.09, 0.12, 0.2), radius=6)
    seg_w = (iw - pad * 2 - 4) / 4.0
    page.rect(ix + pad + 2, sy + 2, seg_w, 9, fill=(0.39, 0.36, 0.93), radius=4)
    for i in range(4):
        page.text(ix + pad + 2 + i * seg_w, sy + 4.2, ["W", "M", "L", "R"][i], 4.4,
                  True, color=WHITE if i == 0 else (0.5, 0.55, 0.67),
                  align="center", width=seg_w)
    # stats
    cw = (iw - pad * 2 - 5) / 2.0
    for i, (label, value) in enumerate([("Hours", "162h"), ("Overtime", "7h"),
                                        ("Late days", "3"), ("Est. pay", "PHP 12k")]):
        bx = ix + pad + (i % 2) * (cw + 5)
        by = sy + 18 + (i // 2) * 24
        page.rect(bx, by, cw, 20, fill=(0.09, 0.12, 0.2), radius=5)
        page.text(bx + 4, by + 3.4, label, 4.4, color=(0.5, 0.55, 0.67))
        page.text(bx + 4, by + 10, value, 6.4, True, color=(0.9, 0.92, 0.96))
    # breakdown
    ry = sy + 70
    page.text(ix + pad, ry, "Breakdown", 5.4, True, color=(0.75, 0.8, 0.9))
    for i in range(6):
        yy = ry + 9 + i * 16
        page.circle(ix + pad + 6, yy + 5, 5, fill=(0.19, 0.2, 0.4))
        bar(page, ix + pad + 15, yy + 2, 34, 3.2, (0.8, 0.84, 0.92))
        bar(page, ix + pad + 15, yy + 7, 24, 2.6, (0.42, 0.47, 0.58))
        bar(page, ix + iw - pad - 22, yy + 3, 22, 4, (0.2, 0.83, 0.6))
    page.rect(ix + pad, iy + ih - 24, iw - pad * 2, 15, fill=(0.13, 0.16, 0.26), radius=7)
    page.text(ix + pad, iy + ih - 20, "Export", 5.2, True,
              color=(0.7, 0.75, 0.9), align="center", width=iw - pad * 2)


def settings_screen(page, x, y, w, h):
    ix, iy, iw, ih = phone_frame(page, x, y, w, h, "Settings")
    pad = 8
    page.text(ix + pad, iy + 8, "Settings", 8.2, True, color=(0.9, 0.92, 0.96))
    sections = [("Shift", 3), ("Data", 3), ("Updates", 2), ("About", 2)]
    yy = iy + 24
    for name, rows in sections:
        page.text(ix + pad, yy, name.upper(), 4.2, True, color=(0.35, 0.5, 0.8))
        yy += 7
        page.rect(ix + pad, yy, iw - pad * 2, rows * 16 + 4,
                  fill=(0.09, 0.12, 0.2), radius=6)
        for r in range(rows):
            ry = yy + 4 + r * 16
            bar(page, ix + pad + 5, ry + 3, 30, 3.0, (0.75, 0.8, 0.9))
            if (r + len(name)) % 3 == 0:
                page.rect(ix + iw - pad - 18, ry + 2, 13, 7, fill=(0.16, 0.55, 0.4), radius=3.5)
                page.circle(ix + iw - pad - 8.5, ry + 5.5, 2.6, fill=WHITE)
            else:
                page.text(ix + iw - pad - 20, ry + 2.4, ">", 5, color=(0.4, 0.45, 0.57))
        yy += rows * 16 + 10


def storage_tree(page, x, y, w):
    """Downloads/AttendanceTracker drawn as a tree with connector lines."""
    rows = [
        (0, "Download/", None),
        (1, "AttendanceTracker/", None),
        (2, "backups/", "newest copy + older copies"),
        (2, "updates/", "downloaded updates"),
        (2, "exports/", "Excel and CSV files"),
    ]
    row_h = 24
    indent = 20
    top = y
    last_at_depth = {}
    for index, (depth, name, note) in enumerate(rows):
        yy = top + index * row_h
        bx = x + depth * indent
        if depth > 0:
            parent_index = last_at_depth.get(depth - 1, index - 1)
            parent_x = x + (depth - 1) * indent + 5
            page.line(parent_x, top + parent_index * row_h + 9, parent_x, yy + 9,
                      RULE, 0.9)
            page.line(parent_x, yy + 9, bx - 4, yy + 9, RULE, 0.9)
        last_at_depth[depth] = index
        if depth == 2:
            page.rect(bx, yy, 88, 18, fill=(0.96, 0.97, 1.0), stroke=RULE,
                      sw=0.7, radius=4)
            page.text(bx + 6, yy + 5, name, 8.0, True, color=PRIMARY_DK)
            page.text(bx + 94, yy + 5.4, note, 7.6, color=MUTED)
        else:
            page.text(bx, yy + 3, name, 8.6, True, color=INK)
    return len(rows) * row_h + 26


def update_flow(page, x, y, w):
    steps = [
        ("1", "The app looks for a new version",
         "Once a day, or when you tap Check for updates"),
        ("2", "It compares that with what you have",
         "If yours is already the newest, nothing happens"),
        ("3", "You decide whether to download",
         "The size and the release notes are shown first"),
        ("4", "The update downloads",
         "A progress bar shows how far along it is"),
        ("5", "Android installs it",
         "You tap Install on the screen that appears"),
    ]
    box_h = 30
    gap = 11
    yy = y
    for num, title, note in steps:
        page.circle(x + 9, yy + box_h / 2, 8.4, fill=PRIMARY)
        page.text(x + 0.6, yy + box_h / 2 - 3.2, num, 8.2, True, color=WHITE,
                  align="center", width=17)
        page.rect(x + 24, yy, w - 24, box_h, fill=SOFT, radius=5)
        page.text(x + 34, yy + 6, title, 8.6, True, color=INK)
        page.text(x + 34, yy + 19, note, 7.6, color=MUTED)
        if num != "5":
            page.arrow(x + 9, yy + box_h + 1, x + 9, yy + box_h + gap - 1,
                       color=FAINT, width=1.0, head=4.4)
        yy += box_h + gap
    return yy - y


# ------------------------------------------------------------------- content

def cover(doc):
    page = doc.new_page(dark=True)
    # subtle corner glow
    page.circle(W * 0.86, 40, 150, fill=(0.09, 0.11, 0.24))
    page.circle(-20, H - 60, 130, fill=(0.07, 0.09, 0.2))

    logo_path = os.path.join(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))), "logo_512.png")
    if os.path.exists(logo_path):
        doc.add_image("Im1", logo_path, bg=(11, 16, 32))
        size = 92
        page.image((W - size) / 2, 132, size, size, "Im1")

    page.text(0, 252, "ATTENDANCE TRACKER", 9.2, True, color=(0.42, 0.85, 0.95),
              align="center", width=W)
    page.text(0, 272, "User Manual", 40, True, color=WHITE, align="center", width=W)
    page.text(0, 322, "Time in, break, overtime and payroll reports",
              12, False, color=(0.62, 0.68, 0.8), align="center", width=W)

    page.rect(W / 2 - 26, 352, 52, 2.4, fill=(0.39, 0.36, 0.93), radius=1.2)

    page.text(0, 378, "Version 1.0.0  \u00b7  Android 5.0 and newer",
              9.4, False, color=(0.55, 0.61, 0.74), align="center", width=W)

    # feature strip
    chips = ["Works offline", "Autosave", "Auto restore", "Auto update", "Excel + CSV"]
    total = 0
    widths = [text_width(c, 7.6, True) + 18 for c in chips]
    total = sum(widths) + 6 * (len(chips) - 1)
    cx = (W - total) / 2
    for chip, cw in zip(chips, widths):
        page.rect(cx, 412, cw, 19, fill=COVER_SOFT, stroke=(0.16, 0.2, 0.33),
                  sw=0.7, radius=9.5)
        page.text(cx, 417.5, chip, 7.6, True, color=(0.72, 0.78, 0.9),
                  align="center", width=cw)
        cx += cw + 6

    page.line(88, 700, W - 88, 700, (0.16, 0.2, 0.33), 0.8)
    page.text(0, 716, "Attendance Tracker 1.0.0", 9.0, True, color=(0.62, 0.68, 0.8),
              align="center", width=W)
    page.text(0, 732, "Everything stays on this device. No account, no cloud, no tracking.",
              8.4, False, color=(0.45, 0.51, 0.64), align="center", width=W)


def contents_page(doc):
    page = doc.new_page()
    page.h1("Contents", "What is inside")
    return page


def fill_contents(page, entries):
    y = page.y + 4
    for title, sub, number in entries:
        page.text(MARGIN_L, y, title, 10.2, True, color=INK)
        page.text(MARGIN_L + 2, y + 13, sub, 8.2, color=MUTED)
        page.text(MARGIN_L, y, str(number), 10.2, True, color=PRIMARY,
                  align="right", width=CONTENT_W)
        page.line(MARGIN_L, y + 22, MARGIN_L + CONTENT_W, y + 22, RULE, 0.5)
        y += 32
    page.y = y

    page.space(12)
    page.note("Conventions",
              "Bold text is a label you will see on screen. Dark blocks are literal "
              "values or file names. Each chapter starts on a new page, so you "
              "can print just the part you need.")

    page.h2("Quick start", 6)
    for i, step in enumerate([
        "Open Team and add yourself with your shift window and rate.",
        "Go to Dashboard and tap Check in when you start work.",
        "Tap Break before you step away and Resume when you are back.",
        "Tap Check out at the end of the day, then review it in History.",
    ], 1):
        page.circle(MARGIN_L + 7, page.y + 5.6, 6.8, fill=PRIMARY)
        page.text(MARGIN_L + 0.6, page.y + 2.8, str(i), 6.8, True, color=WHITE,
                  align="center", width=13)
        for line in wrap(step, 9.2, CONTENT_W - 24):
            page.text(MARGIN_L + 22, page.y, line, 9.2, color=INK_SOFT)
            page.y += 13.2
        page.y += 3


def page_overview(doc):
    page = doc.new_page()
    page.h1("01 \u00b7 Overview", "What the app does")
    page.para("Attendance Tracker is a simple time clock that lives on one phone. You tap "
              "a button when you start work, when you step away and when you go home. The "
              "app remembers the exact time of every tap, adds the hours up, and puts the "
              "totals in a report you can hand to whoever does payroll.")
    page.para("There is nothing to sign up for \u2014 no account, no monthly fee. Your "
              "records stay on the phone, and a copy is kept in the Downloads folder so "
              "you can move it somewhere safer whenever you like.")

    page.h2("The five screens")
    page.table(
        ["Screen", "What you do there"],
        [["Dashboard", "Check in, take a break, check out, log overtime, undo a wrong tap."],
         ["History", "Look at any month, filter by person, open a day to fix or delete it."],
         ["Team", "Add people, set their shift hours, work days and hourly rate."],
         ["Reports", "Totals and a per-person breakdown for a week or a month, saved as Excel or CSV."],
         ["Settings", "Shift rules, currency, backups, restore and updates."]],
        [110, 380])

    page.h2("A normal day", 8)
    used = day_flow(page, MARGIN_L, page.y, CONTENT_W)
    page.y += used + 10

    page.note("Nothing leaves the phone",
              "Your records are not uploaded anywhere. The app works with no signal, and "
              "the only time it uses the internet is to check whether a newer version of "
              "itself is available.")

    page.h2("Who it is for", 6)
    page.bullets([
        "A small team with one phone at the door or the front desk.",
        "Freelancers and remote workers who bill by the hour.",
        "Anyone who needs a plain record of when they started and stopped.",
    ])
    page.end()


def page_install(doc):
    page = doc.new_page()
    page.h1("02 \u00b7 Getting started", "Install and first run")

    page.h2("Installing", 4)
    steps = [
        "Copy the app file to the phone. It ends in .apk and is usually sent as a link or a chat attachment.",
        "Tap the file in your file manager. Android asks whether to allow installs from that app \u2014 allow it once.",
        "Open Attendance Tracker. The first launch sets everything up and creates a starter profile.",
    ]
    for i, step in enumerate(steps, 1):
        page.circle(MARGIN_L + 8, page.y + 6, 7.6, fill=PRIMARY)
        page.text(MARGIN_L + 1, page.y + 2.8, str(i), 7.6, True, color=WHITE,
                  align="center", width=15)
        lines = wrap(step, 9.3, CONTENT_W - 28)
        for j, line in enumerate(lines):
            page.text(MARGIN_L + 26, page.y, line, 9.3, color=INK_SOFT)
            page.y += 13.4
        page.y += 5

    page.space(6)
    page.h2("The first launch, in order", 6)
    page.bullets([
        "The app checks that its saved data is readable. If something is wrong with it, "
        "the newest backup from Downloads is put back automatically.",
        "If there is no one in the team yet, a starter profile is created so the "
        "dashboard is never empty.",
        "The dashboard opens with today's record already loaded, ready for your first punch.",
    ])

    page.h2("Granting the update permission", 10)
    page.para("Android does not let one app install another unless you say so. The "
              "first time an update is ready, the phone opens the \u201cInstall unknown "
              "apps\u201d screen for Attendance Tracker. Turn the switch on and go back \u2014 "
              "the download is kept, so the install carries on where it stopped.")
    page.note("You stay in control",
              "The app never installs anything by itself. It downloads the update, then "
              "Android shows you the normal install screen and waits for you to tap "
              "Install or Cancel.",
              color=WARN, tint=(1.0, 0.98, 0.94))

    page.h2("What you need", 4)
    page.bullets([
        "Android 5.0 or newer. The app is dark-themed by design.",
        "About 12 MB of free space for the app, plus room for your backups.",
        "An internet connection only when you check for updates.",
    ])

    page.h2("If you reinstall", 6)
    page.para("Uninstalling removes the app's own copy of your data, but it does not "
              "touch the AttendanceTracker folder in Downloads. The next time you open the "
              "app it finds those backups and puts the newest one back, so a reinstall "
              "does not cost you your history.")

    page.h2("Where to start", 8)
    page.table(
        ["If you want to...", "Go to"],
        [["Record your own time", "Dashboard, then the big button"],
         ["Fix a punch you forgot", "History, tap the day, then Edit"],
         ["Add a new hire", "Team, then the round + button"],
         ["Change the shift window", "Settings, Shift section"],
         ["Send hours to payroll", "Reports, then Export"]],
        [190, 296], size=8.6, leading=11.2)
    page.end()


def page_dashboard(doc):
    page = doc.new_page()
    page.h1("03 \u00b7 Dashboard", "The screen you use every day")

    page.para("The dashboard is the home screen. Everything on it refers to one person "
              "\u2014 the profile shown in the chip at the top \u2014 and to today's date.")

    screen_w = 186
    screen_h = 360
    dash_screen(page, MARGIN_L, page.y + 12, screen_w, screen_h)

    callouts = [
        ("Ring", "Progress through the required minutes. It fills live, once per second."),
        ("Status pill", "Working, On break, Done, Not started or Absent."),
        ("Primary button", "Changes with the state: Check in, Break, Resume, Check out."),
        ("Stat cards", "Worked, break, overtime and lateness for today only."),
        ("Timeline", "Every punch of the day, newest last, with its exact time."),
        ("Bottom bar", "Moves between Dashboard, History, Team and Reports."),
    ]
    cx = MARGIN_L + screen_w + 22
    cw = CONTENT_W - screen_w - 22
    yy = page.y + 6
    for label, body in callouts:
        page.text(cx, yy, label, 8.6, True, color=PRIMARY_DK)
        yy += 11
        for line in wrap(body, 8.4, cw):
            page.text(cx, yy, line, 8.4, color=INK_SOFT)
            yy += 11.4
        yy += 7

    page.y = page.y + screen_h + 26
    page.h2("Punching in and out", 4)
    page.para("Tap the big button. The app writes the current time into the next empty "
              "slot, saves it straight away and writes a backup a moment later. The button "
              "always tells you what the next tap will do, so you never have to remember "
              "which slot is next.")
    page.bullets([
        "Check in \u2192 Break \u2192 Resume \u2192 Check out, then a second break cycle if your "
        "shift has two breaks.",
        "Made a mistake? Tap Undo. It clears the most recent punch only, never the whole day.",
        "Need to fix an older punch? Tap Edit today to open the same editor the History "
        "screen uses.",
    ])
    page.note("Reading the ring",
              "The arc is worked minutes divided by required minutes, so a full ring means "
              "the shift is complete. The number inside is worked time, the line under it "
              "is the requirement, and both refresh once a second while the screen is open.")
    page.end()


def page_punch_flow(doc):
    page = doc.new_page()
    page.h1("03 \u00b7 Dashboard", "How punches become hours")

    page.para("A day is a set of In/Out pairs. An In starts a working segment, an Out "
              "ends it and starts a break. The app walks the punches in order, so a day "
              "with only an In and an Out still produces sensible totals.")

    page.space(10)
    punch_timeline(page, MARGIN_L, page.y, CONTENT_W)
    page.y += 74

    page.h2("The six slots", 6)
    page.table(
        ["Slot", "What it is", "Meaning"],
        [["1", "Check in / Break", "Start of the shift, then the first break."],
         ["2", "Resume / Break", "Back from break, then the second break."],
         ["3", "Resume / Check out", "Second return, then the end of the day."],
         ["OT", "Overtime", "Optional pair, counted on its own."]],
        [46, 140, 300])

    page.h2("What each number means", 8)
    page.table(
        ["Figure", "Computed from"],
        [["Worked", "Sum of every In to the next Out. An open segment counts up to now."],
         ["Break", "Sum of every Out to the next In."],
         ["Late", "Check-in minus shift start, when it exceeds the grace minutes."],
         ["Undertime", "Required minutes minus worked, never below zero."],
         ["Overtime", "The OT pair when set, otherwise minutes worked past shift end."],
         ["Required", "Shift length minus the configured break allowance."]],
        [90, 396])

    page.note("Two overtime modes",
              "Pair mode counts only the overtime you log yourself, using the overtime "
              "buttons on the dashboard. After-shift mode counts anything worked past the "
              "end of the shift, which suits people who stay late without logging it "
              "separately. Switch modes in Settings > Shift.")
    page.end()


def page_history(doc):
    page = doc.new_page()
    page.h1("04 \u00b7 History", "Looking back")

    screen_w = 186
    screen_h = 300
    history_screen(page, MARGIN_L, page.y + 12, screen_w, screen_h)

    cx = MARGIN_L + screen_w + 22
    cw = CONTENT_W - screen_w - 22
    yy = page.y + 6
    for label, body in [
        ("Month header", "Arrows step one month at a time. The summary strip below "
                         "covers the month you are looking at."),
        ("Filter", "Narrow the list to a single person, or leave it on Everyone."),
        ("Rows", "Date on the left, status badge and total hours on the right. "
                 "Tap a row to open the day."),
    ]:
        page.text(cx, yy, label, 8.6, True, color=PRIMARY_DK)
        yy += 11
        for line in wrap(body, 8.4, cw):
            page.text(cx, yy, line, 8.4, color=INK_SOFT)
            yy += 11.4
        yy += 8

    page.y = page.y + screen_h + 28
    page.h2("Opening a day", 4)
    page.para("Tapping a row opens a detail card with every punch, the computed totals "
              "and the note field. From there you can:")
    page.bullets([
        "Edit \u2014 open the full editor with all eight time fields.",
        "Delete \u2014 remove the record entirely, after a confirmation.",
        "Mark leave or holiday \u2014 keeps the date on the calendar without counting it "
        "as worked time.",
    ])

    page.h2("The day editor", 8)
    page.para("The editor shows eight time fields, a status picker and a note. Times "
              "accept both 12-hour and 24-hour input: 9:42 AM, 9:42am, 21:42 and 9 PM all "
              "work. Anything that cannot be parsed is rejected before saving, and the "
              "preview at the bottom recalculates as you type.")
    page.note("Status is derived, not typed",
              "When a record has punches, the status is recalculated from them: Present, "
              "Late, Half day, Incomplete or In progress. Leave, holiday and absent are "
              "the only statuses you set by hand.")
    page.end()


def page_team(doc):
    page = doc.new_page()
    page.h1("05 \u00b7 Team", "People and shifts")

    screen_w = 186
    screen_h = 280
    team_screen(page, MARGIN_L, page.y + 12, screen_w, screen_h)

    cx = MARGIN_L + screen_w + 22
    cw = CONTENT_W - screen_w - 22
    yy = page.y + 6
    page.text(cx, yy, "One list, one form", 8.6, True, color=PRIMARY_DK)
    yy += 12
    for line in wrap("The list shows every person with their initials, position and "
                     "shift window. Tap a row to edit, tap the round button to add "
                     "someone new.", 8.4, cw):
        page.text(cx, yy, line, 8.4, color=INK_SOFT)
        yy += 11.4
    yy += 8
    page.text(cx, yy, "Archiving keeps history", 8.6, True, color=PRIMARY_DK)
    yy += 12
    for line in wrap("Turning Active off hides the person from the dashboard picker "
                     "without touching their records, so old months still add up.",
                     8.4, cw):
        page.text(cx, yy, line, 8.4, color=INK_SOFT)
        yy += 11.4

    page.y = page.y + screen_h + 28
    page.h2("Employee fields", 4)
    page.table(
        ["Field", "Used for"],
        [["Name", "Shown everywhere; initials are used for the avatar."],
         ["Position", "Free text, shown under the name in the list."],
         ["Code", "Optional employee number or nickname."],
         ["Hourly rate", "Estimated pay in Reports. Leave it at 0 to hide pay figures."],
         ["Shift start / end", "The window used for lateness and overtime."],
         ["Work days", "Which weekdays count as working days, Monday to Sunday."],
         ["Active", "Whether the person appears on the dashboard."]],
        [100, 386])

    page.note("Overnight shifts",
              "If the end time is earlier than the start time, the app treats the shift "
              "as crossing midnight. A 22:00 to 06:00 shift works without extra setup.")
    page.end()


def page_reports(doc):
    page = doc.new_page()
    page.h1("06 \u00b7 Reports", "Turning days into numbers")

    screen_w = 186
    screen_h = 256
    reports_screen(page, MARGIN_L, page.y + 12, screen_w, screen_h)

    cx = MARGIN_L + screen_w + 22
    cw = CONTENT_W - screen_w - 22
    yy = page.y + 6
    for label, body in [
        ("Period", "This week, this month, last month, or any two days from the calendar."),
        ("Range", "Tap a start day, then an end day. Show applies it, Clear empties it."),
        ("Four totals", "Hours worked, overtime, late days and estimated pay."),
        ("Breakdown", "One row per person: days, overtime, late count and hours."),
        ("One file per export",
         "Every export is a new dated file, so nothing is overwritten. The Excel file "
         "also has a Summary sheet that sums each person."),
    ]:
        page.text(cx, yy, label, 8.6, True, color=PRIMARY_DK)
        yy += 11
        for line in wrap(body, 8.4, cw):
            page.text(cx, yy, line, 8.4, color=INK_SOFT)
            yy += 11.4
        yy += 8

    page.y = page.y + screen_h + 24
    page.h2("Estimating pay", 4)
    page.para("Pay is worked hours times the hourly rate, plus overtime hours times the "
              "rate times the overtime multiplier. It is an estimate, not a payslip: no "
              "tax, deductions or allowances are applied.")
    page.table(
        ["Period", "Range used"],
        [["This week", "Monday to Sunday of the current week."],
         ["This month", "First to last day of the current month."],
         ["Last month", "First to last day of the previous month."],
         ["Range", "Any two days, picked on a calendar."]],
        [90, 396])

    page.h2("Exporting", 8)
    page.para("Tap Export and pick a format. Either file lands in "
              "Download/AttendanceTracker/exports, opens straight from the Downloads "
              "folder, and carries the same sixteen columns.")
    page.code(["Date, Employee, Check In, Break 1 Start, Break 1 End,",
               "Break 2 Start, Break 2 End, Check Out, OT In, OT Out,",
               "Worked (h), Break (min), OT (h), Late (min), Status, Note"],
              size=7.9)
    page.table(
        ["Format", "Good for"],
        [["Excel (.xlsx)", "Totals you can add up: numbers stay numbers, and a second "
                           "sheet sums each person."],
         ["CSV (.csv)", "Feeding another system: plain text, opens anywhere."]],
        [110, 376], size=8.4, leading=11.0, pad=5)
    page.end()


def page_settings(doc):
    page = doc.new_page()
    page.h1("07 \u00b7 Settings", "Every switch explained")

    screen_w = 176
    screen_h = 280
    settings_screen(page, MARGIN_L, page.y + 10, screen_w, screen_h)
    page.y = page.y + screen_h + 20

    page.table(
        ["Setting", "Default", "Effect"],
        [["Shift start / end", "9:00 AM - 6:00 PM", "Window for lateness and overtime."],
         ["Grace minutes", "5", "How late you can be before it counts as late."],
         ["Lunch break", "60 min", "Subtracted from the required minutes."],
         ["Coffee break", "20 min", "Added to the break allowance."],
         ["Half day below", "240 min", "Worked minutes under this become Half day."],
         ["Overtime mode", "Pair", "Pair counts logged overtime; After shift counts the tail."],
         ["OT multiplier", "1.25", "Applied to overtime hours in the pay estimate."],
         ["Currency", "PHP", "Prefix used in Reports."],
         ["Autosave", "On", "Writes a backup after every change."],
         ["Backup now", "-", "Writes an extra dated backup right away."],
         ["Restore", "-", "Pick a backup from Downloads, or browse for a file."],
         ["Check for updates", "-", "Asks the server right away, without waiting a day."],
         ["Check automatically", "On", "Looks for a new build once a day."]],
        [104, 96, 286], size=8.2, leading=10.6, pad=5)

    page.note("Changing the shift later",
              "Shift edits apply to future calculations, not to stored punches. History "
              "recomputes on every open, so a corrected shift start fixes the late "
              "counts for past days too.")
    page.end()


def page_storage(doc):
    page = doc.new_page()
    page.h1("08 \u00b7 Your data", "Where everything is kept")

    page.para("Your records are saved twice. One copy lives inside the app and is the one "
              "you see on screen. A second copy is written to the Downloads folder every "
              "few seconds, so a crash, a flat battery or a reinstall never costs you more "
              "than the last few seconds of work.")

    page.h2("The Downloads folder", 4)
    used = storage_tree(page, MARGIN_L, page.y, CONTENT_W)
    page.y += used

    page.h2("What is inside backups", 8)
    page.table(
        ["File", "What it is"],
        [["attendance_latest.db", "The newest copy. Replaced after every change."],
         ["attendance_<date>_<time>.db",
          "An older copy, kept once an hour. The oldest one is removed when there are "
          "too many."]],
        [150, 336], size=8.2, leading=10.6, pad=5)

    page.h2("Saving as you go", 6)
    page.para("There is no Save button, and you do not need one. Two and a half seconds "
              "after any change the app writes the whole thing out again and swaps it in "
              "one step, so you never end up with a half-written file. Once an hour it "
              "also keeps a dated copy, so you can go back to how things looked a few "
              "days ago.")
    page.bullets([
        "The newest copy is what the app falls back to when something goes wrong.",
        "The dated copies are for going further back, when a mistake was made days ago.",
        "The app keeps 20 dated copies by default. You can raise that in Settings.",
    ])

    page.h2("Putting a backup back", 10)
    page.para("Every time the app opens it checks that its copy is readable. If it is "
              "missing or damaged, the newest backup that is still good is copied back "
              "into place. Before anything is replaced, the damaged file is set aside, so "
              "nothing disappears without a trace.")
    page.note("Restoring by hand",
              "Settings > Restore lists every backup with its date and size. You can also "
              "pick a backup file from anywhere else on the phone. After a restore the app "
              "reopens so you are looking at the restored data, not the old data.")
    page.end()


def page_updates(doc):
    page = doc.new_page()
    page.h1("09 \u00b7 Updates", "Getting a newer version")

    page.para("The app can update itself, so you do not have to hunt for a new file every "
              "time something is fixed. It looks for a newer version once a day, and it "
              "never downloads anything until you agree to it.")

    page.space(6)
    used = update_flow(page, MARGIN_L, page.y, CONTENT_W)
    page.y += used + 12

    page.h2("What you will see", 2)
    page.bullets([
        "The version number, so you can tell what you are getting.",
        "A short note about what changed in that version.",
        "The size of the download, and a progress bar once it starts.",
        "Two buttons: download now, or remind me later.",
    ])

    page.h2("Before you update", 8)
    page.bullets([
        "Make sure the phone has some charge and a little free space.",
        "Do not uninstall the old version first. The update goes on top of it.",
        "Your records, your settings and your team list are all kept.",
    ])

    page.note("If the install is blocked",
              "Open the phone's Settings, find Apps > Attendance Tracker > Install unknown "
              "apps, and turn it on. Then open the app and start the update again.",
              color=WARN, tint=(1.0, 0.98, 0.94))

    page.h2("If you would rather not be asked", 4)
    page.para("Turn off Check automatically in Settings. The app will stop looking on its "
              "own, and you can still tap Check now whenever you feel like it, so nothing "
              "is ever forced on you.")
    page.end()


def page_trouble(doc):
    page = doc.new_page()
    page.h1("10 \u00b7 Troubleshooting", "When something looks wrong")

    page.table(
        ["Symptom", "What to do"],
        [["The dashboard says Absent but I worked",
          "The record has no check-in. Open Edit today and set the times, or use Undo if "
          "a punch was written to the wrong day."],
         ["Hours look too low",
          "A break was never closed. Every Out must be followed by an In, otherwise the "
          "time after it is counted as break."],
         ["Overtime is zero",
          "In Pair mode only overtime you log yourself is counted. Switch to After shift "
          "in Settings if you want time past the end of the shift counted instead."],
         ["Reports is empty",
          "Check the period segment and the person filter. A picked range stays until "
          "you choose another one."],
         ["Restore does nothing",
          "The file has to be a backup this app wrote. Damaged files are skipped on "
          "purpose, so pick a different one."],
         ["Update will not install",
          "Allow installs from this app in the phone's settings, then try again. If the "
          "download was cut off, delete it from the updates folder and download it again."],
         ["The app restarts after a restore",
          "Expected. The app reopens so you are looking at the restored data."],
         ["Everything is empty after reinstalling",
          "The app puts the newest backup back on first launch. If it is still empty, "
          "open Settings > Restore and pick the file by hand."],
         ["The ring never moves",
          "The ring only counts a working segment. If the status is On break or Done, "
          "there is nothing to count until you resume."],
         ["The wrong person is on the dashboard",
          "Tap the name chip at the top and pick someone else. The choice is remembered "
          "until you change it."],
         ["A punch shows 0:00",
          "The time was typed as a bare number. Write 9:00, 9am or 21:00 instead."],
         ["CSV opens as one long column",
          "Some regions expect semicolons. Open it in Google Sheets directly, or export "
          "the Excel file instead, which keeps the columns intact."]],
        [150, 336], size=8.2, leading=10.8, pad=6)

    page.h2("Resetting everything", 8)
    page.para("There is no factory reset button, and that is deliberate \u2014 a single "
              "tap should not be able to erase payroll history. To start clean, delete "
              "the app, then delete the AttendanceTracker folder in Downloads. A fresh "
              "install starts again with fresh records and a starter profile.")

    page.h2("Diagnostics", 8)
    page.bullets([
        "Settings > Storage info shows how much space your records use, how many backups "
        "you have and when the last one was written.",
        "If the app reopens by itself when you launch it, it put a backup back. That "
        "happens when its own copy was missing or damaged.",
        "Before updating, keep at least one older backup. If the new version misbehaves, "
        "restoring takes one tap.",
        "If something looks wrong, copy the AttendanceTracker folder to a computer before "
        "reinstalling, so you still have the old copies.",
    ])
    page.end()


def page_privacy(doc):
    page = doc.new_page()
    page.h1("11 \u00b7 Reference", "Statuses, permissions and limits")

    page.h2("What the app asks for", 2)
    page.table(
        ["You may see", "What it is for"],
        [["Internet", "Only to check for a newer version and download it."],
         ["Install apps", "Only to install an update you approved."],
         ["Files (older phones)", "Only to write your backups into Downloads."]],
        [150, 336], size=8.4, leading=11.2)

    page.para("Nothing is sent anywhere. There is no account, no analytics and no crash "
              "reporting. The app works with no signal at all \u2014 the internet is used "
              "only when it looks for an update.")

    page.h2("What the statuses mean", 8)
    page.table(
        ["Status", "Meaning"],
        [["Present", "Checked in and out, on time, enough hours."],
         ["Late", "The same, but the check-in was past the grace time."],
         ["Half day", "Worked less than the half-day minimum."],
         ["Incomplete", "A check-in with no check-out, and not open right now."],
         ["In progress", "Still working, or on a break right now."],
         ["Absent", "No check-in at all."],
         ["Leave", "Set by hand. Not counted as worked."],
         ["Holiday", "Set by hand. Not counted as worked."]],
        [90, 396], size=8.4, leading=11.2)

    page.h2("Good to know", 8)
    page.bullets([
        "One phone holds the records. There is no syncing between phones.",
        "Times are recorded to the minute.",
        "Pay figures are estimates; tax, loans and government deductions are not included.",
        "Restoring an older backup throws away everything recorded after it.",
        "Changing phones? Copy the AttendanceTracker folder across, then restore from it.",
    ])

    page.space(10)
    page.rect(MARGIN_L, page.y, CONTENT_W, 52, fill=SOFT, radius=6)
    page.text(MARGIN_L + 16, page.y + 12, "Attendance Tracker 1.0.0", 10, True, color=INK)
    page.text(MARGIN_L + 16, page.y + 26, "For Android 5.0 and newer", 8.4, color=MUTED)
    page.text(MARGIN_L + 16, page.y + 38,
              "This manual describes the app as shipped in version 1.0.0.", 8.4, color=MUTED)
    page.y += 62
    page.end()


# ---------------------------------------------------------------------- main

def build_doc():
    """Lays out every page and returns the document, unfinished."""
    doc = Doc()
    cover(doc)
    toc = contents_page(doc)

    entries = []

    def mark(title, sub):
        entries.append((title, sub, len(doc.pages)))

    mark("Overview", "What the app does and who it is for")
    page_overview(doc)
    mark("Getting started", "Install, first launch, update permission")
    page_install(doc)
    mark("Dashboard", "Punching, breaks, undo and the day editor")
    page_dashboard(doc)
    mark("Punch rules", "How punches become hours, break and overtime")
    page_punch_flow(doc)
    mark("History", "Browsing months and correcting a day")
    page_history(doc)
    mark("Team", "People, shifts, work days and rates")
    page_team(doc)
    mark("Reports", "Totals, breakdown and export")
    page_reports(doc)
    mark("Settings", "Every switch, with its default")
    page_settings(doc)
    mark("Your data", "Folders, autosave and auto restore")
    page_storage(doc)
    mark("Updates", "Checking, downloading and installing")
    page_updates(doc)
    mark("Troubleshooting", "Common symptoms and their fix")
    page_trouble(doc)
    mark("Reference", "Permissions, statuses and known limits")
    page_privacy(doc)

    fill_contents(toc, entries)
    return doc


def build(path):
    doc = build_doc()
    size = doc.finish(path)
    return len(doc.pages), size


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "AttendanceTracker-Manual.pdf")
    pages, size = build(out)
    print("wrote %s" % out)
    print("pages: %d   size: %.1f KB" % (pages, size / 1024.0))


if __name__ == "__main__":
    main()
