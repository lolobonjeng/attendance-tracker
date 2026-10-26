#!/usr/bin/env python3
"""Writes update.json from a built APK, so the manifest always matches the file.

Usage:
    python3 tools/make_update.py <apk> <versionCode> <versionName> [notes] [-o out.json]

The size and the SHA-256 are read from the APK itself. A wrong size only breaks
the progress bar, but a wrong digest makes the app refuse to install the update,
so both are taken from the file rather than typed by hand.

Publish order matters: upload the APK to the release first, then commit the new
update.json. The other way round, phones see a new versionCode and download a
404.
"""

import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_APK_URL = ("https://github.com/lolobonjeng/attendance-tracker/releases/"
                   "latest/download/attendance-tracker.apk")


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 256), b""):
            h.update(chunk)
    return h.hexdigest()


def main(argv):
    out = os.path.join(ROOT, "update.json")
    args = []
    i = 0
    while i < len(argv):
        if argv[i] in ("-o", "--out"):
            if i + 1 >= len(argv):
                print("missing path after %s" % argv[i])
                return 2
            out = argv[i + 1]
            i += 2
            continue
        args.append(argv[i])
        i += 1

    if len(args) < 3:
        print(__doc__.strip())
        return 2

    apk, code, name = args[0], int(args[1]), args[2]
    notes = args[3] if len(args) > 3 else ""
    if not os.path.isfile(apk):
        print("no such file: %s" % apk)
        return 2

    size = os.path.getsize(apk)
    if size <= 0:
        print("file is empty: %s" % apk)
        return 2

    manifest = {
        "versionCode": code,
        "versionName": name,
        "url": DEFAULT_APK_URL,
        "notes": notes,
        "size": size,
        "sha256": digest(apk),
        "mandatory": False,
    }
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
        handle.write("\n")

    print("wrote %s" % out)
    print("  version %s (%d)" % (name, code))
    print("  size    %d bytes (%.2f MB)" % (size, size / 1048576.0))
    print("  sha256  %s" % manifest["sha256"])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
