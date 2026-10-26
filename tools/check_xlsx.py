#!/usr/bin/env python3
"""Validates an .xlsx produced by XlsxWriter, the way a spreadsheet app reads it.

Checks the package parts, the relationship graph, the content-type overrides, then
every sheet's cells: header styling, numeric values, inline strings, escaping and
that each cell reference matches its row.

Usage:
    python3 tools/check_xlsx.py path/to/file.xlsx
"""

import sys
import zipfile
import xml.etree.ElementTree as ET

MAIN = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
RELS = "{http://schemas.openxmlformats.org/package/2006/relationships}"
CT = "{http://schemas.openxmlformats.org/package/2006/content-types}"

errors = []


def err(msg):
    errors.append(msg)


def col_name(index):
    out = ""
    n = index + 1
    while n > 0:
        rem = (n - 1) % 26
        out = chr(ord("A") + rem) + out
        n = (n - 1) // 26
    return out


def main():
    if len(sys.argv) < 2:
        print("usage: check_xlsx.py file.xlsx")
        return 2
    path = sys.argv[1]

    if not zipfile.is_zipfile(path):
        print("not a zip archive: %s" % path)
        return 1

    with zipfile.ZipFile(path) as book:
        names = set(book.namelist())
        required = ["[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels", "xl/styles.xml"]
        for part in required:
            if part not in names:
                err("missing part: %s" % part)
        if errors:
            return report()

        def parse(part):
            try:
                return ET.fromstring(book.read(part))
            except ET.ParseError as exc:
                err("%s is not valid XML: %s" % (part, exc))
                return None

        types = parse("[Content_Types].xml")
        workbook = parse("xl/workbook.xml")
        rels = parse("xl/_rels/workbook.xml.rels")
        parse("xl/styles.xml")
        if errors:
            return report()

        overrides = {o.get("PartName") for o in types.iter(CT + "Override")}
        for needed in ["/xl/workbook.xml", "/xl/styles.xml"]:
            if needed not in overrides:
                err("content types has no override for %s" % needed)

        rel_targets = {}
        for rel in rels.iter(RELS + "Relationship"):
            rel_targets[rel.get("Id")] = rel.get("Target")

        sheets = list(workbook.iter(MAIN + "sheet"))
        if not sheets:
            err("workbook declares no sheets")

        r_ns = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
        for sheet in sheets:
            name = sheet.get("name")
            rid = sheet.get(r_ns)
            target = rel_targets.get(rid)
            if not target:
                err("sheet %r points at missing relationship %s" % (name, rid))
                continue
            part = "xl/" + target.lstrip("/").replace("xl/", "", 1) \
                if not target.startswith("/") else target.lstrip("/")
            if part not in names:
                err("sheet %r target %s is not in the package" % (name, target))
                continue
            if "/" + part not in overrides:
                err("content types has no override for %s" % part)
            check_sheet(parse(part), name)

    cross_check(path)
    return report()


def check_sheet(root, name):
    if root is None:
        return
    data = root.find(MAIN + "sheetData")
    if data is None:
        err("sheet %r has no sheetData" % name)
        return

    rows = list(data.findall(MAIN + "row"))
    if not rows:
        err("sheet %r is empty" % name)
        return

    for row_index, row in enumerate(rows):
        expected_row = row_index + 1
        if int(row.get("r")) != expected_row:
            err("sheet %r: row numbered %s where %d was expected"
                % (name, row.get("r"), expected_row))
        for col_index, cell in enumerate(row.findall(MAIN + "c")):
            ref = cell.get("r")
            expected_ref = col_name(col_index) + str(expected_row)
            if ref != expected_ref:
                err("sheet %r: cell ref %s where %s was expected"
                    % (name, ref, expected_ref))

            if cell.get("t") == "inlineStr":
                node = cell.find(MAIN + "is/" + MAIN + "t")
                if node is None:
                    err("sheet %r: %s is an inline string with no text" % (name, ref))
                    continue
                if row_index == 0 and cell.get("s") != "1":
                    err("sheet %r: header cell %s is not styled" % (name, ref))
            else:
                value = cell.find(MAIN + "v")
                if value is None or value.text is None:
                    err("sheet %r: %s has no value" % (name, ref))
                    continue
                try:
                    float(value.text)
                except ValueError:
                    err("sheet %r: %s is not a number (%r)" % (name, ref, value.text))
                if row_index == 0:
                    err("sheet %r: header cell %s is numeric" % (name, ref))

    header = [c.find(MAIN + "is/" + MAIN + "t").text
              for c in rows[0].findall(MAIN + "c")]
    if len(header) != len(rows[0].findall(MAIN + "c")):
        err("sheet %r: header has an unreadable cell" % name)

    auto = root.find(MAIN + "autoFilter")
    if auto is not None:
        want = "A1:%s%d" % (col_name(len(header) - 1), len(rows))
        if auto.get("ref") != want:
            err("sheet %r: autoFilter is %s, expected %s"
                % (name, auto.get("ref"), want))


def cross_check(path):
    """Second opinion from a real spreadsheet library, when one is installed."""
    try:
        import openpyxl
    except ImportError:
        print("note: openpyxl not installed, skipping the independent read")
        return
    try:
        book = openpyxl.load_workbook(path)
    except Exception as exc:  # noqa: BLE001 - any failure means the file is unreadable
        err("openpyxl cannot open the workbook: %s" % exc)
        return
    cells = 0
    for sheet in book.worksheets:
        for _ in sheet.iter_rows(values_only=True):
            cells += 1
    print("openpyxl: %d sheet(s), %d row(s) read" % (len(book.worksheets), cells))


def report():
    for item in errors[:30]:
        print("ERR  " + item)
    print("%d errors" % len(errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
