package com.cph.att.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal .xlsx writer: a ZIP of OOXML parts, no third-party library.
 *
 * <p>Only what the app needs is supported: several sheets, a bold header row, inline
 * strings, numbers, column widths and an auto filter. Excel, Google Sheets and WPS all
 * open the result.
 *
 * <p>Pure Java on purpose, so it can be unit tested outside Android.
 */
public final class XlsxWriter {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final List<Sheet> sheets = new ArrayList<>();

    public Sheet addSheet(String name) {
        Sheet sheet = new Sheet(name);
        sheets.add(sheet);
        return sheet;
    }

    public int sheetCount() {
        return sheets.size();
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(32 * 1024);
        ZipOutputStream zip = new ZipOutputStream(buffer);
        try {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", STYLES);
            for (int i = 0; i < sheets.size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml",
                        sheets.get(i).toXml(i == 0));
            }
        } finally {
            zip.close();
        }
        return buffer.toByteArray();
    }

    // ---------------------------------------------------------------- package

    private static void put(ZipOutputStream zip, String name, String body) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(UTF8));
        zip.closeEntry();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escape(sheets.get(i).name))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets></workbook>");
        return sb.toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\"")
                .append(" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static final String STYLES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\">"
                    + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                    + "</fonts>"
                    + "<fills count=\"3\">"
                    + "<fill><patternFill patternType=\"none\"/></fill>"
                    + "<fill><patternFill patternType=\"gray125\"/></fill>"
                    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF4F46E5\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                    + "</fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"4\">"
                    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf>"
                    + "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                    + "<xf numFmtId=\"1\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                    + "</cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>";

    // ------------------------------------------------------------------ sheet

    public static final class Sheet {

        private static final int STYLE_HEADER = 1;
        private static final int STYLE_DECIMAL = 2;
        private static final int STYLE_INTEGER = 3;

        private final String name;
        private final List<Object[]> rows = new ArrayList<>();
        private final List<Integer> widths = new ArrayList<>();
        private boolean frozen = true;
        private boolean filtered = true;

        Sheet(String name) {
            this.name = safeName(name);
        }

        public Sheet header(Object... cells) {
            rows.add(cells);
            return this;
        }

        public Sheet row(Object... cells) {
            rows.add(cells);
            return this;
        }

        /** Column widths in characters, in order. */
        public Sheet widths(int... chars) {
            for (int c : chars) {
                widths.add(c);
            }
            return this;
        }

        public Sheet freezeHeader(boolean on) {
            frozen = on;
            return this;
        }

        public Sheet filter(boolean on) {
            filtered = on;
            return this;
        }

        String toXml(boolean first) {
            StringBuilder sb = new StringBuilder(4 * 1024);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

            sb.append("<sheetViews><sheetView");
            if (first) {
                sb.append(" tabSelected=\"1\"");
            }
            sb.append(" workbookViewId=\"0\">");
            if (frozen && rows.size() > 1) {
                sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
            }
            sb.append("</sheetView></sheetViews>");

            sb.append("<sheetFormatPr defaultRowHeight=\"15\"/>");

            if (!widths.isEmpty()) {
                sb.append("<cols>");
                for (int i = 0; i < widths.size(); i++) {
                    sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                            .append("\" width=\"").append(widths.get(i))
                            .append("\" customWidth=\"1\"/>");
                }
                sb.append("</cols>");
            }

            sb.append("<sheetData>");
            int columns = 0;
            for (Object[] row : rows) {
                columns = Math.max(columns, row.length);
            }
            for (int r = 0; r < rows.size(); r++) {
                Object[] row = rows.get(r);
                sb.append("<row r=\"").append(r + 1).append("\"");
                if (r == 0) {
                    sb.append(" ht=\"20\" customHeight=\"1\"");
                }
                sb.append(">");
                for (int c = 0; c < row.length; c++) {
                    appendCell(sb, col(c), r + 1, row[c], r == 0);
                }
                sb.append("</row>");
            }
            sb.append("</sheetData>");

            if (filtered && rows.size() > 1 && columns > 0) {
                sb.append("<autoFilter ref=\"A1:").append(col(columns - 1))
                        .append(rows.size()).append("\"/>");
            }
            sb.append("</worksheet>");
            return sb.toString();
        }

        private static void appendCell(StringBuilder sb, String col, int row, Object value,
                                       boolean header) {
            if (value == null) {
                return;
            }
            String ref = col + row;
            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                boolean whole = value instanceof Integer || value instanceof Long
                        || value instanceof Short || value instanceof Byte;
                sb.append("<c r=\"").append(ref).append("\" s=\"")
                        .append(whole ? STYLE_INTEGER : STYLE_DECIMAL).append("\">");
                if (whole) {
                    sb.append("<v>").append(((Number) value).longValue()).append("</v>");
                } else {
                    sb.append("<v>")
                            .append(String.format(Locale.US, "%.2f", number))
                            .append("</v>");
                }
                sb.append("</c>");
                return;
            }
            sb.append("<c r=\"").append(ref).append("\"");
            if (header) {
                sb.append(" s=\"").append(STYLE_HEADER).append("\"");
            }
            sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(value.toString()))
                    .append("</t></is></c>");
        }

        private static String safeName(String raw) {
            String cleaned = raw == null ? "Sheet" : raw;
            cleaned = cleaned.replaceAll("[\\[\\]:*?/\\\\]", " ");
            cleaned = cleaned.trim();
            if (cleaned.startsWith("'")) {
                cleaned = cleaned.substring(1);
            }
            if (cleaned.endsWith("'")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (cleaned.isEmpty()) {
                cleaned = "Sheet";
            }
            return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
        }
    }

    // ----------------------------------------------------------------- helpers

    /** 0-based column index to spreadsheet letters. */
    static String col(int index) {
        StringBuilder sb = new StringBuilder(3);
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (c == '\n') {
                        sb.append("&#10;");
                    } else if (c < 0x20) {
                        sb.append(' ');
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
