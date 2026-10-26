#!/usr/bin/env python3
"""Validates the generated manual PDF.

Checks the container (header, xref offsets, stream lengths, page tree) and then
re-walks every text run to make sure nothing spills past the margins or overlaps
another run. Geometry uses the same Helvetica metrics as the generator.

Usage:
    python3 tools/check_pdf.py [path.pdf]
"""

import os
import re
import sys
import zlib

W, H = 595.276, 841.890
MARGIN_L, MARGIN_R = 54.0, 54.0
MARGIN_T, MARGIN_B = 54.0, 54.0

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_manual import text_width  # noqa: E402

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


# ------------------------------------------------------------------ container

def check_structure(data):
    if not data.startswith(b"%PDF-1.4"):
        err("missing %PDF-1.4 header")
        return None, None

    tail = data[-2048:]
    marker = tail.rfind(b"startxref")
    if marker < 0:
        err("no startxref")
        return None, None
    start = int(re.match(rb"startxref\s+(\d+)", tail[marker:]).group(1))
    if data[start:start + 4] != b"xref":
        err("startxref points at %r, not 'xref'" % data[start:start + 8])
        return None, None

    head = data[start:start + 400].split(b"\n")
    count = int(head[1].split()[1])

    offsets = {}
    pos = start
    # skip "xref", the "0 N" header, and the free entry for object 0
    for _ in range(3):
        pos = data.index(b"\n", pos) + 1
    for num in range(1, count):
        line = data[pos:pos + 20]
        offset = int(line[:10])
        if data[offset:offset + 20].split(b" ")[0] != str(num).encode():
            err("xref entry %d points to the wrong object" % num)
        offsets[num] = offset
        pos += 20

    # every object must be terminated and streams must match their /Length
    for num, offset in offsets.items():
        end = data.find(b"endobj", offset)
        if end < 0:
            err("object %d is not terminated" % num)
            continue
        body = data[offset:end]
        match = re.search(rb"/Length\s+(\d+)", body)
        if match and b"stream\n" in body:
            declared = int(match.group(1))
            stream_at = body.index(b"stream\n") + 7
            actual = body.rindex(b"\nendstream") - stream_at
            if declared != actual:
                err("object %d: /Length %d but %d bytes in stream"
                    % (num, declared, actual))
    return offsets, count


# ------------------------------------------------------------------ text runs

def text_runs(content):
    """Yields (font, size, x, baseline, text) for every Tj in the stream."""
    runs = []
    pos = 0
    length = len(content)
    while True:
        start = content.find(b"BT", pos)
        if start < 0:
            break
        end = content.find(b"ET", start)
        if end < 0:
            break
        block = content[start:end]
        pos = end + 2

        font = re.search(rb"/(F\d)\s+([\d.]+)\s+Tf", block)
        matrix = re.search(rb"1 0 0 1 ([\d.-]+) ([\d.-]+) Tm", block)
        string = extract_string(block)
        if not font or not matrix or string is None:
            continue
        runs.append((font.group(1).decode(), float(font.group(2)),
                     float(matrix.group(1)), float(matrix.group(2)), string))
    return runs


def extract_string(block):
    """Pulls the literal string out of ( ... ) Tj, honouring backslash escapes."""
    idx = block.find(b"(")
    if idx < 0:
        return None
    out = bytearray()
    i = idx + 1
    while i < len(block):
        ch = block[i]
        if ch == 0x5C:  # backslash
            if i + 1 < len(block):
                out.append(block[i + 1])
                i += 2
                continue
            break
        if ch == 0x29:  # ')'
            break
        out.append(ch)
        i += 1
    return out.decode("cp1252", "replace")


def audit(pages):
    for number, runs in enumerate(pages, 1):
        boxes = []
        for font, size, x, baseline, text in runs:
            if not text.strip():
                continue
            bold = font == "F2"
            width = text_width(text, size, bold)
            top = H - baseline - size * 0.76
            if x < MARGIN_L - 1.0:
                err("p%d: %r starts left of the margin at x=%.1f" % (number, text, x))
            if x + width > W - MARGIN_R + 1.5:
                err("p%d: %r overflows the right margin (ends at %.1f)"
                    % (number, text, x + width))
            if top < MARGIN_T - 22:
                err("p%d: %r sits above the top margin (y=%.1f)" % (number, text, top))
            # the footer band lives inside the bottom margin on purpose
            if top + size > H - 24:
                err("p%d: %r sits below the footer band (y=%.1f)" % (number, text, top))
            boxes.append((x, top, x + width, top + size, text))

        # text-on-text collisions, ignoring tiny slivers from descenders
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                ax0, ay0, ax1, ay1, at = boxes[i]
                bx0, by0, bx1, by1, bt = boxes[j]
                ox = min(ax1, bx1) - max(ax0, bx0)
                oy = min(ay1, by1) - max(ay0, by0)
                if ox > 1.5 and oy > 1.5:
                    err("p%d: %r overlaps %r by %.1f x %.1f pt"
                        % (number, at, bt, ox, oy))


# ----------------------------------------------------------------------- main

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        root, "AttendanceTracker-Manual.pdf")
    data = open(path, "rb").read()

    offsets, count = check_structure(data)
    if offsets is None:
        report(path, 0)
        return

    pages_root = re.search(rb"/Type /Pages /Kids \[([^\]]*)\] /Count (\d+)", data)
    if not pages_root:
        err("no page tree")
        report(path, 0)
        return
    kids = pages_root.group(1).split()
    declared = int(pages_root.group(2))
    if declared != len(kids) // 3:
        err("/Count says %d but there are %d kids" % (declared, len(kids) // 3))

    pages = []
    for kid in kids[0::3]:
        num = int(kid)
        obj = data[offsets[num]:data.find(b"endobj", offsets[num])]
        media = re.search(rb"/MediaBox \[0 0 ([\d.]+) ([\d.]+)\]", obj)
        if not media:
            err("page object %d has no MediaBox" % num)
            continue
        if abs(float(media.group(1)) - W) > 0.5 or abs(float(media.group(2)) - H) > 0.5:
            err("page object %d is not A4 (%s x %s)"
                % (num, media.group(1).decode(), media.group(2).decode()))
        content_num = int(re.search(rb"/Contents (\d+) 0 R", obj).group(1))
        body = data[offsets[content_num]:data.find(b"endobj", offsets[content_num])]
        stream = body[body.index(b"stream\n") + 7:body.rindex(b"endstream") - 1]
        pages.append(text_runs(stream))

    if len(pages) != declared:
        err("built %d pages but declared %d" % (len(pages), declared))

    audit(pages)

    # spot-check that the manual actually contains its sections
    everything = " ".join(r[4] for page in pages for r in page)
    for phrase in ["User Manual", "Dashboard", "Check in", "Troubleshooting",
                   "AttendanceTracker/", "Half day", "Restore"]:
        if phrase not in everything:
            err("expected phrase missing from the text layer: %r" % phrase)

    report(path, len(pages))


def report(path, page_count):
    print("file: %s" % os.path.basename(path))
    print("pages: %d" % page_count)
    for item in warnings:
        print("WARN " + item)
    for item in errors[:40]:
        print("ERR  " + item)
    if len(errors) > 40:
        print("... %d more errors" % (len(errors) - 40))
    print()
    print("%d errors, %d warnings" % (len(errors), len(warnings)))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
