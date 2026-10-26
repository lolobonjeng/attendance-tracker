#!/usr/bin/env python3
"""Generates R, BuildConfig and ViewBinding stubs so the app sources can be
type-checked with plain javac (no Android SDK required)."""
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(os.environ.get("STUB_OUT", os.path.expanduser("~/dev/attstub")),
                   "gen", "com", "cph", "att")

ANDROID = "{http://schemas.android.com/apk/res/android}"

TYPE_MAP = {
    "LinearLayout": "android.widget.LinearLayout",
    "FrameLayout": "android.widget.FrameLayout",
    "TextView": "android.widget.TextView",
    "ImageView": "android.widget.ImageView",
    "ProgressBar": "android.widget.ProgressBar",
    "ScrollView": "android.widget.ScrollView",
    "Space": "android.view.View",
    "Button": "android.widget.Button",
    "EditText": "android.widget.EditText",
    "androidx.appcompat.widget.AppCompatButton": "androidx.appcompat.widget.AppCompatButton",
    "androidx.recyclerview.widget.RecyclerView": "androidx.recyclerview.widget.RecyclerView",
    "androidx.core.widget.NestedScrollView": "androidx.core.widget.NestedScrollView",
    "com.google.android.material.textfield.TextInputLayout":
        "com.google.android.material.textfield.TextInputLayout",
    "com.google.android.material.textfield.TextInputEditText":
        "com.google.android.material.textfield.TextInputEditText",
    "com.google.android.material.switchmaterial.SwitchMaterial":
        "com.google.android.material.switchmaterial.SwitchMaterial",
    "com.cph.att.ui.RingView": "com.cph.att.ui.RingView",
    "com.cph.att.ui.BottomNav": "com.cph.att.ui.BottomNav",
}


def camel(name):
    return name


def binding_name(layout):
    return "".join(part.capitalize() for part in layout.split("_")) + "Binding"


def collect_values():
    out = {"string": set(), "color": set(), "style": set(), "dimen": set(), "bool": set()}
    folder = os.path.join(RES, "values")
    for name in os.listdir(folder):
        if not name.endswith(".xml"):
            continue
        root = ET.parse(os.path.join(folder, name)).getroot()
        for node in root:
            ident = node.get("name")
            if not ident:
                continue
            tag = node.tag
            if tag in out:
                out[tag].add(ident)
    return out


def collect_files():
    out = {}
    for folder in os.listdir(RES):
        path = os.path.join(RES, folder)
        if not os.path.isdir(path) or folder.startswith("values"):
            continue
        kind = folder.split("-")[0]
        for name in os.listdir(path):
            base = os.path.splitext(name)[0]
            out.setdefault(kind, set()).add(base)
    return out


def collect_ids():
    ids = set()
    for name in os.listdir(os.path.join(RES, "layout")):
        root = ET.parse(os.path.join(RES, "layout", name)).getroot()
        for node in root.iter():
            ident = node.get(ANDROID + "id")
            if ident and ident.startswith("@+id/"):
                ids.add(ident[5:])
    return ids


def write_r(values, files, ids):
    os.makedirs(OUT, exist_ok=True)
    lines = ["package com.cph.att;", "", "public final class R {"]
    counter = 0x7f010000
    for kind in sorted(values.keys()):
        lines.append("    public static final class %s {" % kind)
        for name in sorted(values[kind]):
            counter += 1
            safe = name.replace(".", "_").replace("-", "_")
            lines.append("        public static final int %s = %d;" % (safe, counter))
        lines.append("    }")
    for kind in sorted(files.keys()):
        lines.append("    public static final class %s {" % kind)
        for name in sorted(files[kind]):
            if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", name):
                continue
            counter += 1
            lines.append("        public static final int %s = %d;" % (name, counter))
        lines.append("    }")
    lines.append("    public static final class id {")
    for name in sorted(ids):
        counter += 1
        lines.append("        public static final int %s = %d;" % (name, counter))
    lines.append("    }")
    lines.append("}")
    with open(os.path.join(OUT, "R.java"), "w") as handle:
        handle.write("\n".join(lines))

    with open(os.path.join(OUT, "BuildConfig.java"), "w") as handle:
        handle.write("package com.cph.att;\n\npublic final class BuildConfig {\n"
                     "    public static final int VERSION_CODE = 1;\n"
                     "    public static final String VERSION_NAME = \"1.0.0\";\n}\n")


def write_bindings():
    layouts = {}
    for name in os.listdir(os.path.join(RES, "layout")):
        if not name.endswith(".xml"):
            continue
        layout = os.path.splitext(name)[0]
        root = ET.parse(os.path.join(RES, "layout", name)).getroot()
        root_type = TYPE_MAP.get(root.tag)
        if root_type is None:
            raise SystemExit("unknown root tag %s in %s" % (root.tag, name))
        fields = []
        for node in root.iter():
            ident = node.get(ANDROID + "id")
            if not ident:
                continue
            ident = ident[5:]
            if node.tag == "include":
                child = node.get("layout", "").replace("@layout/", "")
                ftype = binding_name(child)
            else:
                ftype = TYPE_MAP.get(node.tag)
                if ftype is None:
                    raise SystemExit("unknown tag %s in %s" % (node.tag, name))
            fields.append((ident, ftype))
        layouts[layout] = (root_type, fields)

    os.makedirs(os.path.join(OUT, "databinding"), exist_ok=True)
    for layout, (root_type, fields) in layouts.items():
        cls = binding_name(layout)
        lines = ["package com.cph.att.databinding;", "",
                 "public final class %s {" % cls,
                 "    private final %s root;" % root_type,
                 "    public %s(%s root) { this.root = root; }" % (cls, root_type),
                 "    public %s getRoot() { return root; }" % root_type,
                 "    public static %s inflate(android.view.LayoutInflater inflater) { return null; }" % cls,
                 "    public static %s inflate(android.view.LayoutInflater inflater, "
                 "android.view.ViewGroup parent, boolean attach) { return null; }" % cls,
                 "    public static %s bind(android.view.View view) { return null; }" % cls]
        for name, ftype in fields:
            lines.append("    public %s %s;" % (ftype, name))
        lines.append("}")
        with open(os.path.join(OUT, "databinding", cls + ".java"), "w") as handle:
            handle.write("\n".join(lines))

    return layouts


def main():
    values = collect_values()
    files = collect_files()
    ids = collect_ids()
    write_r(values, files, ids)
    layouts = write_bindings()
    print("R + BuildConfig + %d bindings written to %s" % (len(layouts), OUT))


if __name__ == "__main__":
    main()
