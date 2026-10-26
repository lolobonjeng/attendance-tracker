#!/data/data/com.termux/files/usr/bin/bash
# Compiles and runs the pure-Java core tests. No Android SDK required.
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="${TMPDIR:-/data/data/com.termux/files/home/dev}/att-test-build"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes"

echo "compiling core..."
javac -encoding UTF-8 -d "$BUILD/classes" "$ROOT"/app/src/main/java/com/cph/att/core/*.java

echo "compiling tests..."
javac -encoding UTF-8 -cp "$BUILD/classes" -d "$BUILD/classes" \
    "$ROOT"/tools/tests/test/CoreTest.java

echo "running..."
java -cp "$BUILD/classes" test.CoreTest

echo
echo "compiling xlsx writer..."
javac -encoding UTF-8 -d "$BUILD/classes" \
    "$ROOT"/app/src/main/java/com/cph/att/backup/XlsxWriter.java

echo "compiling xlsx test..."
javac -encoding UTF-8 -cp "$BUILD/classes" -d "$BUILD/classes" \
    "$ROOT"/tools/tests/test/XlsxTest.java

echo "building sample workbook..."
java -cp "$BUILD/classes" test.XlsxTest "$BUILD/sample.xlsx"

echo "validating workbook..."
python3 "$ROOT"/tools/check_xlsx.py "$BUILD/sample.xlsx"
