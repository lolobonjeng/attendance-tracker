package test;

import com.cph.att.backup.XlsxWriter;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Builds a workbook and writes it to the path given as the first argument.
 * tools/check_xlsx.py then opens it the way a spreadsheet app would.
 */
public final class XlsxTest {

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "test.xlsx";

        XlsxWriter book = new XlsxWriter();

        XlsxWriter.Sheet days = book.addSheet("Days");
        days.widths(12, 22, 11, 11, 10, 11, 9, 9, 12, 26);
        days.header("Date", "Employee", "Check in", "Check out", "Worked (h)",
                "Break (min)", "OT (h)", "Late (min)", "Status", "Note");
        days.row("2026-10-26", "Ana Reyes", "9:00 AM", "6:12 PM", 8.2, 60, 0.0, 0,
                "Present", "R&D <shift> & \"quotes\"");
        days.row("2026-10-27", "Ana Reyes", "9:14 AM", "6:00 PM", 7.77, 60, 0.0, 14,
                "Late", "");
        days.row("2026-10-28", "Ben Cruz", "", "", 0.0, 0, 0.0, 0, "Absent", "sick");

        XlsxWriter.Sheet summary = book.addSheet("Summary");
        summary.widths(24, 8, 11, 11, 10, 14);
        summary.header("Employee", "Days", "Hours", "Overtime", "Late days", "Est. pay");
        summary.row("Ana Reyes", 2, 15.97, 0.0, 1, 2395.5);
        summary.row("Ben Cruz", 1, 0.0, 0.0, 0, 0.0);

        byte[] bytes = book.toBytes();
        FileOutputStream out = new FileOutputStream(path);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        System.out.println("wrote " + path + " (" + bytes.length + " bytes)");
    }
}
