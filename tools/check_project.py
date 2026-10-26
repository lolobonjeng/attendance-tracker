#!/usr/bin/env python3
"""Static checks for the Android project (no SDK needed).

- every XML file is well formed
- every @type/name reference resolves to a real resource
- every R.type.name reference in Java resolves
- ViewBinding fields exist in the matching layout
- manifest activities have classes
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
JAVA = os.path.join(ROOT, "app", "src", "main", "java")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def read(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


# ---------------------------------------------------------------- collect res

strings = set()
colors = set()
styles = set()
dimens = set()
bools = set()

for name in os.listdir(os.path.join(RES, "values")):
    if not name.endswith(".xml"):
        continue
    path = os.path.join(RES, "values", name)
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        err("XML parse error in values/%s: %s" % (name, exc))
        continue
    for node in tree.getroot():
        tag = node.tag
        ident = node.get("name")
        if not ident:
            continue
        if tag == "string":
            strings.add(ident)
        elif tag == "color":
            colors.add(ident)
        elif tag == "style":
            styles.add(ident)
        elif tag == "dimen":
            dimens.add(ident)
        elif tag == "bool":
            bools.add(ident)

# values-night mirrors values; not merged on purpose (same names expected)

file_res = {}
for folder in os.listdir(RES):
    full = os.path.join(RES, folder)
    if not os.path.isdir(full):
        continue
    if folder.startswith("values"):
        continue
    kind = folder.split("-")[0]
    for name in os.listdir(full):
        if name.endswith(".xml") or name.endswith(".png") or name.endswith(".webp"):
            file_res.setdefault(kind, set()).add(os.path.splitext(name)[0])
        elif os.path.isdir(os.path.join(full, name)):
            for inner in os.listdir(os.path.join(full, name)):
                file_res.setdefault(kind, set()).add(os.path.splitext(inner)[0])

file_res.setdefault("drawable", set())
file_res.setdefault("layout", set())
file_res.setdefault("mipmap", set())
file_res.setdefault("anim", set())
file_res.setdefault("xml", set())


def exists(kind, name):
    if kind == "string":
        return name in strings
    if kind == "color":
        return name in colors
    if kind == "style":
        return name in styles
    if kind == "dimen":
        return name in dimens
    if kind == "bool":
        return name in bools
    return name in file_res.get(kind, set())


# --------------------------------------------------------------- parse layouts

layout_ids = {}
include_children = {}

for name in os.listdir(os.path.join(RES, "layout")):
    if not name.endswith(".xml"):
        continue
    path = os.path.join(RES, "layout", name)
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        err("XML parse error in layout/%s: %s" % (name, exc))
        continue
    base = os.path.splitext(name)[0]
    ids = set()
    includes = {}
    for node in tree.iter():
        ident = node.get("{http://schemas.android.com/apk/res/android}id")
        if ident and ident.startswith("@+id/"):
            ids.add(ident[5:])
        if node.tag == "include":
            inc_id = node.get("{http://schemas.android.com/apk/res/android}id")
            layout = node.get("layout")
            if inc_id and layout:
                includes[inc_id[5:]] = layout.replace("@layout/", "")
    layout_ids[base] = ids
    include_children[base] = includes

# ------------------------------------------------------------------ xml checks

ref_pattern = re.compile(r"@(\+)?(drawable|string|color|style|dimen|bool|layout|mipmap|anim|xml)/([A-Za-z0-9_.]+)")

for folder, dirs, files in os.walk(RES):
    for name in files:
        if not name.endswith(".xml"):
            continue
        path = os.path.join(folder, name)
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            err("XML parse error: %s (%s)" % (os.path.relpath(path, ROOT), exc))
            continue
        text = read(path)
        for match in ref_pattern.finditer(text):
            plus, kind, res_name = match.groups()
            if plus:
                continue
            if not exists(kind, res_name):
                err("missing @%s/%s referenced by %s" % (kind, res_name,
                                                          os.path.relpath(path, ROOT)))

# ---------------------------------------------------------------- java checks

r_pattern = re.compile(r"\bR\.(drawable|string|color|style|dimen|layout|mipmap|anim|xml|id)\.([A-Za-z0-9_]+)")
binding_decl = re.compile(r"\b([A-Z][A-Za-z0-9]*Binding)\s+([a-z][A-Za-z0-9]*)\b")
chain_pattern = re.compile(r"\b([a-z][A-Za-z0-9]*)\.([a-z][A-Za-z0-9_]*)(?:\.([a-z][A-Za-z0-9_]*))?")

METHOD_WHITELIST = {
    "getRoot", "inflate", "bind", "get", "set", "equals", "hashCode", "toString",
    "length", "trim", "isEmpty", "charAt", "substring", "split", "contains",
    "setText", "getText", "setVisibility", "setOnClickListener", "setEnabled",
    "setAlpha", "setColorFilter", "setImageResource", "setBackgroundResource",
    "setBackgroundTintList", "setLayoutParams", "setPadding", "setTag", "getTag",
    "setChecked", "isChecked", "setProgress", "setProgressText", "setTitle",
    "setMessage", "inputText", "input", "dismiss", "show", "isShowing",
    "setCurrent", "setSubText", "setCenterText", "setColors", "setData",
    "setAdapter", "setLayoutManager", "setCancelable", "setHint", "requestFocus",
    "addTextChangedListener", "setSelection", "setInputType", "getWindow",
    "setContentView", "run", "onClick", "onItemClick", "accept", "call",
    "post", "postDelayed", "removeCallbacks", "execute", "shutdown",
    "setOnApplyWindowInsetsListener", "getLayoutParams", "setPaddingRelative",
    "getPaddingLeft", "getPaddingTop", "getPaddingRight", "getPaddingBottom",
    "animate", "alpha", "translationY", "setDuration", "setInterpolator",
    "withEndAction", "start", "performHapticFeedback", "getResources",
    "getColor", "getString", "getDisplayMetrics", "setTypeface", "setTextSize",
    "setLetterSpacing", "setGravity", "setOrientation", "setClickable",
    "setFocusable", "setWeightSum", "addView", "removeAllViews", "getChildCount",
    "setTextColor", "setBackground", "getBackground", "setElevation",
    "setSystemUiVisibility", "addFlags", "clearFlags", "setStatusBarColor",
    "setNavigationBarColor", "getDecorView", "findViewById", "setSoftInputMode",
    "setAttributes", "setBackgroundDrawable", "setLayout", "getAttributes",
    "addCategory", "setType", "setData", "setDataAndType", "startActivity",
    "startActivityForResult", "finish", "finishAffinity", "isFinishing",
    "isDestroyed", "runOnUiThread", "getIntent", "getLongExtra",
    "getStringExtra", "getBooleanExtra", "putExtra", "removeExtra",
    "getApplicationContext", "getPackageName", "getContentResolver",
    "getDatabasePath", "getCacheDir", "getFilesDir", "getSystemService",
    "getWritableDatabase", "getReadableDatabase", "rawQuery", "execSQL",
    "query", "insert", "update", "delete", "close", "beginTransaction",
    "setTransactionSuccessful", "endTransaction", "moveToFirst", "moveToNext",
    "getString", "getInt", "getLong", "getDouble", "getColumnIndex",
    "getColumnIndexOrThrow", "getColumnNames", "getType", "isNull", "getCount",
    "getPosition", "notifyDataSetChanged", "setHasFixedSize", "setItemAnimator",
    "setClipToPadding", "setNestedScrollingEnabled", "postDelayed",
}

binding_fields = {}

for folder, dirs, files in os.walk(JAVA):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(folder, name)
        text = read(path)
        rel = os.path.relpath(path, ROOT)

        for match in r_pattern.finditer(text):
            kind, res_name = match.groups()
            if kind == "id":
                continue
            if not exists(kind, res_name):
                err("missing R.%s.%s in %s" % (kind, res_name, rel))

        for match in binding_decl.finditer(text):
            cls, var = match.groups()
            layout = re.sub(r"(?<!^)(?=[A-Z])", "_", cls[:-len("Binding")]).lower()
            binding_fields.setdefault(rel, {}).setdefault(var, set()).add(layout)

        for var, layouts in binding_fields.get(rel, {}).items():
            for match in chain_pattern.finditer(text):
                if match.group(1) != var:
                    continue
                first = match.group(2)
                second = match.group(3)
                if first in METHOD_WHITELIST:
                    continue
                known = [l for l in layouts if l in layout_ids]
                if not known:
                    warn("%s: unknown layout %s for %s" % (rel, sorted(layouts), var))
                    continue
                if not any(first in layout_ids[l] for l in known):
                    err("%s: %s.%s not found in %s"
                        % (rel, var, first, "/".join(sorted(known))))
                    continue
                if second:
                    children = set()
                    for l in known:
                        child = include_children.get(l, {}).get(first)
                        if child:
                            children.add(child)
                    if children and not any(
                            child in layout_ids and second in layout_ids[child]
                            for child in children):
                        if second in METHOD_WHITELIST:
                            continue
                        err("%s: %s.%s.%s not found in %s"
                            % (rel, var, first, second, "/".join(sorted(children))))

# ---------------------------------------------------------------- manifest

tree = ET.parse(MANIFEST)
package = "com.cph.att"
android_ns = "{http://schemas.android.com/apk/res/android}"
for node in tree.getroot().iter("activity"):
    cls = node.get(android_ns + "name")
    if not cls:
        continue
    simple = cls.replace(".", "/").lstrip("/")
    if simple.startswith(package.replace(".", "/")):
        simple = simple[len(package.replace(".", "/")):].lstrip("/")
    target = os.path.join(JAVA, package.replace(".", "/"), simple + ".java")
    if not os.path.exists(target):
        err("manifest activity without class: %s" % cls)

# ---------------------------------------------------------------- java syntax

for folder, dirs, files in os.walk(JAVA):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(folder, name)
        text = read(path)
        if text.count("{") != text.count("}"):
            err("brace mismatch in %s (%d vs %d)" % (
                os.path.relpath(path, ROOT), text.count("{"), text.count("}")))
        if text.count("(") != text.count(")"):
            err("paren mismatch in %s" % os.path.relpath(path, ROOT))

# ---------------------------------------------------------------- duplicate res
# aapt2 fails the build when two files in the same res folder resolve to the
# same resource name (e.g. ic_launcher.png + ic_launcher.webp).
for folder in sorted(os.listdir(RES)):
    folder_path = os.path.join(RES, folder)
    if not os.path.isdir(folder_path):
        continue
    by_name = {}
    for name in os.listdir(folder_path):
        base = os.path.splitext(name)[0]
        by_name.setdefault(base, []).append(name)
    for base, names in by_name.items():
        if len(names) > 1:
            err("duplicate resource %s/%s: %s"
                % (folder, base, ", ".join(sorted(names))))

# ---------------------------------------------------------------- style parents
# <style name="Text.Body"> with no parent makes aapt2 infer a parent called
# "Text" from the dotted name, which fails linking with
# "resource style/Text not found". Require an explicit parent ("" is allowed).
import glob as _glob
for _path in sorted(_glob.glob(os.path.join(RES, "values*", "*.xml"))):
    try:
        _root = ET.parse(_path).getroot()
    except ET.ParseError:
        continue
    for _style in _root.iter("style"):
        _name = _style.get("name") or ""
        if "." in _name and "parent" not in _style.attrib:
            err("%s: style %s has a dotted name but no parent attribute "
                "(aapt2 will look for style/%s)"
                % (os.path.relpath(_path, ROOT), _name, _name.split(".")[0]))

# ---------------------------------------------------------------- hidden APIs
# Framework members that exist in AOSP but are @hide, so they are missing from
# the public android.jar. The stub-based javac pass can accept them, then the
# real build fails. Add to this list whenever one bites.
HIDDEN_APIS = [
    (r"getContext\(\)\s*\.\s*getDatabasePath",
     "SQLiteOpenHelper.getContext() is @hide - store the Context in a field"),
    (r"\bActivityThread\b", "android.app.ActivityThread is @hide"),
    (r"\bViewRootImpl\b", "android.view.ViewRootImpl is @hide"),
    (r"\bContextImpl\b", "android.app.ContextImpl is @hide"),
]
for folder, dirs, files in os.walk(JAVA):
    for name in files:
        if not name.endswith(".java"):
            continue
        path = os.path.join(folder, name)
        text = read(path)
        for pattern, message in HIDDEN_APIS:
            if re.search(pattern, text):
                err("%s: %s" % (os.path.relpath(path, ROOT), message))

# ---------------------------------------------------------------- report

print("checked %d layouts, %d drawables, %d strings"
      % (len(layout_ids), len(file_res.get("drawable", set())), len(strings)))
for item in warnings:
    print("WARN " + item)
for item in errors:
    print("ERR  " + item)
print()
print("%d errors, %d warnings" % (len(errors), len(warnings)))
sys.exit(1 if errors else 0)
