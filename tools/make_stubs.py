#!/usr/bin/env python3
"""Writes minimal Android/AndroidX stubs so javac can type-check the app.

These stubs are ONLY used for static verification in Termux. They are never
compiled into the APK.

IMPORTANT: a stub must only declare members that exist in the *public* SDK
(the ones present in android.jar). Stubbing a `@hide` member makes javac accept
code that the real build rejects — that is exactly how
`SQLiteOpenHelper.getContext()` slipped through once. When in doubt, leave the
member out: a missing stub is a loud error, an invented stub is a silent one.
"""
import os

OUT = os.path.join(os.path.expanduser("~/dev/attstub"), "src")

FILES = {}

FILES["android/content/Context.java"] = """
package android.content;
import android.content.res.Resources;
import android.content.pm.PackageManager;
public class Context {
    public Context getApplicationContext() { return this; }
    public ContentResolver getContentResolver() { return null; }
    public java.io.File getDatabasePath(String name) { return null; }
    public java.io.File getCacheDir() { return null; }
    public java.io.File getFilesDir() { return null; }
    public Resources getResources() { return null; }
    public String getPackageName() { return ""; }
    public PackageManager getPackageManager() { return null; }
    public String getString(int id) { return ""; }
    public void startActivity(Intent intent) { }
    public void setTheme(int id) { }
    public Object getSystemService(String name) { return null; }
    public int checkSelfPermission(String permission) { return 0; }
}
"""

FILES["android/content/ContentResolver.java"] = """
package android.content;
import android.database.Cursor;
import android.net.Uri;
public class ContentResolver {
    public Uri insert(Uri uri, ContentValues values) { return null; }
    public int delete(Uri uri, String selection, String[] args) { return 0; }
    public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    public java.io.InputStream openInputStream(Uri uri) { return null; }
    public java.io.OutputStream openOutputStream(Uri uri) { return null; }
}
"""

FILES["android/content/ContentValues.java"] = """
package android.content;
public class ContentValues {
    public void put(String key, String value) { }
    public void put(String key, Integer value) { }
    public void put(String key, Long value) { }
    public void put(String key, Double value) { }
    public void put(String key, Float value) { }
    public void put(String key, Boolean value) { }
    public void put(String key, int value) { }
    public void put(String key, long value) { }
    public void put(String key, double value) { }
    public void putNull(String key) { }
}
"""

FILES["android/content/Intent.java"] = """
package android.content;
import android.net.Uri;
public class Intent {
    public static final int FLAG_ACTIVITY_CLEAR_TOP = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x00000001;
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE";
    public Intent() { }
    public Intent(Context context, Class<?> cls) { }
    public Intent(String action) { }
    public Intent(String action, Uri uri) { }
    public Intent addFlags(int flags) { return this; }
    public Intent putExtra(String key, long value) { return this; }
    public Intent putExtra(String key, String value) { return this; }
    public Intent putExtra(String key, boolean value) { return this; }
    public Intent putExtra(String key, int value) { return this; }
    public Intent removeExtra(String key) { return this; }
    public long getLongExtra(String key, long fallback) { return fallback; }
    public String getStringExtra(String key) { return null; }
    public boolean getBooleanExtra(String key, boolean fallback) { return fallback; }
    public Intent setDataAndType(Uri uri, String type) { return this; }
    public Intent setType(String type) { return this; }
    public Intent addCategory(String category) { return this; }
    public Uri getData() { return null; }
}
"""

FILES["android/content/pm/PackageManager.java"] = """
package android.content.pm;
public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public boolean canRequestPackageInstalls() { return true; }
}
"""

FILES["android/content/res/ColorStateList.java"] = """
package android.content.res;
public class ColorStateList {
    public static ColorStateList valueOf(int color) { return null; }
}
"""

FILES["android/content/res/Resources.java"] = """
package android.content.res;
public class Resources {
    public int getColor(int id) { return 0; }
    public String getString(int id) { return ""; }
    public DisplayMetrics getDisplayMetrics() { return new DisplayMetrics(); }
    public static class DisplayMetrics {
        public int widthPixels;
        public int heightPixels;
        public float density = 1f;
    }
}
"""

FILES["android/app/Activity.java"] = """
package android.app;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
public class Activity extends Context {
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    protected void onCreate(Bundle saved) { }
    protected void onResume() { }
    protected void onPause() { }
    protected void onSaveInstanceState(Bundle out) { }
    protected void onDestroy() { }
    protected void onStart() { }
    protected void onStop() { }
    public void setContentView(int layout) { }
    public void setContentView(View view) { }
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return null; }
    public Intent getIntent() { return null; }
    public Window getWindow() { return null; }
    public LayoutInflater getLayoutInflater() { return null; }
    public void finish() { }
    public void finishAffinity() { }
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public void runOnUiThread(Runnable action) { }
    public void startActivityForResult(Intent intent, int code) { }
    protected void onActivityResult(int request, int result, Intent data) { }
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { }
    public void setTitle(CharSequence title) { }
    /** Public since API 5, deprecated but still present on API 34. */
    public void overridePendingTransition(int enterAnim, int exitAnim) { }
}
"""

FILES["android/app/Application.java"] = """
package android.app;
import android.content.Context;
public class Application extends Context {
    public void onCreate() { }
}
"""

FILES["android/app/Dialog.java"] = """
package android.app;
import android.content.Context;
import android.view.View;
import android.view.Window;
public class Dialog {
    public static final int FEATURE_NO_TITLE = 1;
    public Dialog(Context context, int theme) { }
    public void requestWindowFeature(int feature) { }
    public void setContentView(View view) { }
    public void setCancelable(boolean cancelable) { }
    public void show() { }
    public void dismiss() { }
    public boolean isShowing() { return false; }
    public Window getWindow() { return null; }
}
"""

FILES["android/database/Cursor.java"] = """
package android.database;
public interface Cursor {
    int FIELD_TYPE_NULL = 0;
    int FIELD_TYPE_INTEGER = 1;
    int FIELD_TYPE_FLOAT = 2;
    int FIELD_TYPE_STRING = 3;
    boolean moveToFirst();
    boolean moveToNext();
    int getCount();
    int getPosition();
    String getString(int column);
    int getInt(int column);
    long getLong(int column);
    double getDouble(int column);
    int getType(int column);
    boolean isNull(int column);
    int getColumnIndex(String name);
    int getColumnIndexOrThrow(String name);
    String[] getColumnNames();
    void close();
}
"""

FILES["android/database/sqlite/SQLiteException.java"] = """
package android.database.sqlite;
public class SQLiteException extends RuntimeException {
    public SQLiteException() { }
    public SQLiteException(String message) { super(message); }
}
"""

FILES["android/database/sqlite/SQLiteDatabase.java"] = """
package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.File;
public class SQLiteDatabase {
    public static final int CONFLICT_REPLACE = 5;
    public static final int OPEN_READONLY = 1;
    public static final int OPEN_READWRITE = 0;
    public interface CursorFactory { Cursor newCursor(SQLiteDatabase db, CursorDriver driver); }
    public interface CursorDriver { }
    public static SQLiteDatabase openOrCreateDatabase(File file, CursorFactory factory) { return null; }
    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags) { return null; }
    public void execSQL(String sql) { }
    public Cursor rawQuery(String sql, String[] args) { return null; }
    public Cursor query(String table, String[] columns, String selection, String[] args,
                        String groupBy, String having, String orderBy) { return null; }
    public Cursor query(String table, String[] columns, String selection, String[] args,
                        String groupBy, String having, String orderBy, String limit) { return null; }
    public long insert(String table, String nullColumn, ContentValues values) { return 0; }
    public long insertWithOnConflict(String table, String nullColumn, ContentValues values, int conflict) { return 0; }
    public int update(String table, ContentValues values, String where, String[] args) { return 0; }
    public int delete(String table, String where, String[] args) { return 0; }
    public void beginTransaction() { }
    public void setTransactionSuccessful() { }
    public void endTransaction() { }
    public void setForeignKeyConstraintsEnabled(boolean enabled) { }
    public void close() { }
}
"""

FILES["android/database/sqlite/SQLiteOpenHelper.java"] = """
package android.database.sqlite;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
public abstract class SQLiteOpenHelper {
    public SQLiteOpenHelper(Context context, String name, CursorFactory factory, int version) { }
    public SQLiteDatabase getWritableDatabase() { return null; }
    public SQLiteDatabase getReadableDatabase() { return null; }
    public String getDatabaseName() { return null; }
    public void close() { }
    public void onConfigure(SQLiteDatabase db) { }
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
}
"""

FILES["android/graphics/Color.java"] = """
package android.graphics;
public class Color {
    public static final int TRANSPARENT = 0;
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static int argb(int a, int r, int g, int b) { return 0; }
    public static int red(int color) { return 0; }
    public static int green(int color) { return 0; }
    public static int blue(int color) { return 0; }
    public static int parseColor(String value) { return 0; }
}
"""

FILES["android/graphics/ColorFilter.java"] = """
package android.graphics;
public class ColorFilter {
    public ColorFilter() { }
}
"""

FILES["android/graphics/PorterDuff.java"] = """
package android.graphics;
public class PorterDuff {
    public enum Mode { CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN,
                       SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN,
                       MULTIPLY, SCREEN, ADD, OVERLAY }
}
"""

FILES["android/graphics/Typeface.java"] = """
package android.graphics;
public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static Typeface create(String family, int style) { return null; }
    public static Typeface create(String family, int style, boolean italic) { return null; }
}
"""

FILES["android/graphics/Shader.java"] = """
package android.graphics;
public class Shader {
    public enum TileMode { CLAMP, REPEAT, MIRROR }
}
"""

FILES["android/graphics/LinearGradient.java"] = """
package android.graphics;
public class LinearGradient extends Shader {
    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, Shader.TileMode mode) { }
    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions,
                          Shader.TileMode mode) { }
}
"""

FILES["android/graphics/SweepGradient.java"] = """
package android.graphics;
public class SweepGradient extends Shader {
    public SweepGradient(float cx, float cy, int[] colors, float[] positions) { }
    public SweepGradient(float cx, float cy, int color0, int color1) { }
}
"""

FILES["android/graphics/RectF.java"] = """
package android.graphics;
public class RectF {
    public float left, top, right, bottom;
    public RectF() { }
    public RectF(float l, float t, float r, float b) { }
    public void set(float l, float t, float r, float b) { }
    public float centerX() { return 0; }
    public float centerY() { return 0; }
    public float width() { return 0; }
    public float height() { return 0; }
}
"""

FILES["android/graphics/Paint.java"] = """
package android.graphics;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Align { LEFT, CENTER, RIGHT }
    public Paint() { }
    public Paint(int flags) { }
    public void setStyle(Style style) { }
    public void setStrokeWidth(float width) { }
    public void setStrokeCap(Cap cap) { }
    public void setColor(int color) { }
    public void setTextSize(float size) { }
    public float getTextSize() { return 0; }
    public void setTypeface(Typeface typeface) { }
    public void setTextAlign(Align align) { }
    public void setLetterSpacing(float spacing) { }
    public void setShader(Shader shader) { }
    public void setAntiAlias(boolean aa) { }
}
"""

FILES["android/graphics/Canvas.java"] = """
package android.graphics;
public class Canvas {
    public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint paint) { }
    public void drawText(String text, float x, float y, Paint paint) { }
    public void drawCircle(float cx, float cy, float radius, Paint paint) { }
}
"""

FILES["android/graphics/Insets.java"] = """
package android.graphics;
public final class Insets {
    public final int left, top, right, bottom;
    private Insets() { left = 0; top = 0; right = 0; bottom = 0; }
    public static Insets of(int left, int top, int right, int bottom) { return null; }
}
"""

FILES["android/graphics/drawable/Drawable.java"] = """
package android.graphics.drawable;
public class Drawable {
}
"""

FILES["android/graphics/drawable/ColorDrawable.java"] = """
package android.graphics.drawable;
public class ColorDrawable extends Drawable {
    public ColorDrawable(int color) { }
}
"""

FILES["android/net/Uri.java"] = """
package android.net;
import java.io.File;
public class Uri {
    public static Uri parse(String uri) { return null; }
    public static Uri fromFile(File file) { return null; }
    public static Uri withAppendedPath(Uri base, String path) { return null; }
    public String getScheme() { return null; }
    public String getPath() { return null; }
}
"""

FILES["android/os/Build.java"] = """
package android.os;
public class Build {
    public static class VERSION {
        public static final int SDK_INT = 34;
    }
    public static class VERSION_CODES {
        public static final int JELLY_BEAN = 16;
        public static final int JELLY_BEAN_MR1 = 17;
        public static final int LOLLIPOP = 21;
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
    }
}
"""

FILES["android/os/Bundle.java"] = """
package android.os;
public class Bundle {
    public int getInt(String key, int def) { return def; }
    public String getString(String key) { return null; }
    public void putInt(String key, int value) { }
    public void putString(String key, String value) { }
}
"""

FILES["android/os/Looper.java"] = """
package android.os;
public class Looper {
    public static Looper getMainLooper() { return null; }
}
"""

FILES["android/os/Handler.java"] = """
package android.os;
public class Handler {
    public Handler(Looper looper) { }
    public Handler() { }
    public boolean post(Runnable action) { return true; }
    public boolean postDelayed(Runnable action, long delay) { return true; }
    public void removeCallbacks(Runnable action) { }
}
"""

FILES["android/os/Environment.java"] = """
package android.os;
import java.io.File;
public class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static File getExternalStoragePublicDirectory(String type) { return null; }
}
"""

FILES["android/provider/MediaStore.java"] = """
package android.provider;
import android.net.Uri;
public class MediaStore {
    public interface MediaColumns {
        String _ID = "_id";
        String DISPLAY_NAME = "_display_name";
        String MIME_TYPE = "mime_type";
        String RELATIVE_PATH = "relative_path";
        String IS_PENDING = "is_pending";
        String SIZE = "_size";
        String DATE_MODIFIED = "date_modified";
        String DATE_ADDED = "date_added";
    }
    public static final class Downloads implements MediaColumns {
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/downloads");
    }
}
"""

FILES["android/provider/Settings.java"] = """
package android.provider;
public class Settings {
    public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES";
    public static final String ACTION_SECURITY_SETTINGS = "android.settings.SECURITY_SETTINGS";
}
"""

FILES["android/text/Editable.java"] = """
package android.text;
public interface Editable extends CharSequence {
}
"""

FILES["android/text/TextWatcher.java"] = """
package android.text;
public interface TextWatcher {
    void beforeTextChanged(CharSequence s, int start, int count, int after);
    void onTextChanged(CharSequence s, int start, int before, int count);
    void afterTextChanged(Editable s);
}
"""

FILES["android/text/InputType.java"] = """
package android.text;
public interface InputType {
    int TYPE_CLASS_NUMBER = 2;
    int TYPE_CLASS_TEXT = 1;
}
"""

FILES["android/util/AttributeSet.java"] = """
package android.util;
public interface AttributeSet {
}
"""

FILES["android/util/Log.java"] = """
package android.util;
public final class Log {
    public static int d(String tag, String msg) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable t) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable t) { return 0; }
}
"""

FILES["android/util/TypedValue.java"] = """
package android.util;
public class TypedValue {
    public static final int COMPLEX_UNIT_SP = 2;
    public static final int COMPLEX_UNIT_DIP = 1;
}
"""

FILES["android/view/ViewParent.java"] = """
package android.view;
public interface ViewParent {
    ViewParent getParent();
    void requestLayout();
}
"""

FILES["android/view/Gravity.java"] = """
package android.view;
public class Gravity {
    public static final int CENTER = 17;
    public static final int CENTER_HORIZONTAL = 1;
    public static final int CENTER_VERTICAL = 16;
    public static final int TOP = 48;
    public static final int BOTTOM = 80;
    public static final int START = 8388611;
    public static final int END = 8388613;
}
"""

FILES["android/view/HapticFeedbackConstants.java"] = """
package android.view;
public class HapticFeedbackConstants {
    public static final int VIRTUAL_KEY = 1;
    public static final int LONG_PRESS = 0;
}
"""

FILES["android/view/LayoutInflater.java"] = """
package android.view;
import android.content.Context;
public class LayoutInflater {
    public static LayoutInflater from(Context context) { return null; }
    public View inflate(int resource, ViewGroup root) { return null; }
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) { return null; }
}
"""

FILES["android/view/View.java"] = """
package android.view;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 8192;
    public static final int MEASURED_SIZE_MASK = 0x00ffffff;
    public View(Context context) { }
    public View(Context context, AttributeSet attrs) { }
    public View(Context context, AttributeSet attrs, int defStyleAttr) { }
    public Context getContext() { return null; }
    public Resources getResources() { return null; }
    public void setVisibility(int visibility) { }
    public int getVisibility() { return 0; }
    public void setOnClickListener(OnClickListener listener) { }
    public void setOnLongClickListener(OnLongClickListener listener) { }
    public void setBackgroundResource(int resource) { }
    public void setBackground(Drawable drawable) { }
    public void setBackgroundDrawable(Drawable drawable) { }
    public Drawable getBackground() { return null; }
    public void setBackgroundTintList(ColorStateList list) { }
    public void setBackgroundColor(int color) { }
    public void setPadding(int l, int t, int r, int b) { }
    public void setPaddingRelative(int l, int t, int r, int b) { }
    public int getPaddingLeft() { return 0; }
    public int getPaddingTop() { return 0; }
    public int getPaddingRight() { return 0; }
    public int getPaddingBottom() { return 0; }
    public void setLayoutParams(ViewGroup.LayoutParams params) { }
    public ViewGroup.LayoutParams getLayoutParams() { return null; }
    public void setAlpha(float alpha) { }
    public float getAlpha() { return 1f; }
    public void setTranslationY(float y) { }
    public void setElevation(float elevation) { }
    public void setRotation(float rotation) { }
    public void setScaleX(float scale) { }
    public void setScaleY(float scale) { }
    public void setEnabled(boolean enabled) { }
    public boolean isEnabled() { return true; }
    public void setClickable(boolean clickable) { }
    public void setFocusable(boolean focusable) { }
    public void setSelected(boolean selected) { }
    public void setTag(Object tag) { }
    public Object getTag() { return null; }
    public void requestFocus() { }
    public boolean performHapticFeedback(int constant) { return false; }
    public ViewPropertyAnimator animate() { return null; }
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) { }
    public void setSystemUiVisibility(int visibility) { }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public void post(Runnable action) { }
    public void postDelayed(Runnable action, long delay) { }
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) { return null; }
    public ViewParent getParent() { return null; }
    public void setContentDescription(CharSequence description) { }
    public void invalidate() { }
    public void setMinimumHeight(int height) { }
    public void setMinimumWidth(int width) { }
    protected void onAttachedToWindow() { }
    protected void onDetachedFromWindow() { }
    protected void onSizeChanged(int w, int h, int oldw, int oldh) { }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) { }
    protected void onLayout(boolean changed, int l, int t, int r, int b) { }
    protected void onDraw(android.graphics.Canvas canvas) { }
    protected final void setMeasuredDimension(int measuredWidth, int measuredHeight) { }
    public final int getMeasuredWidth() { return 0; }
    public final int getMeasuredHeight() { return 0; }
    public static int getDefaultSize(int size, int measureSpec) { return size; }
    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1;
        public static final int AT_MOST = 2;
        public static int makeMeasureSpec(int size, int mode) { return size; }
        public static int getMode(int spec) { return 0; }
        public static int getSize(int spec) { return 0; }
    }
    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public interface OnApplyWindowInsetsListener {
        WindowInsets onApplyWindowInsets(View v, WindowInsets insets);
    }
}
"""

FILES["android/view/ViewPropertyAnimator.java"] = """
package android.view;
public class ViewPropertyAnimator {
    public ViewPropertyAnimator alpha(float value) { return this; }
    public ViewPropertyAnimator translationY(float value) { return this; }
    public ViewPropertyAnimator translationX(float value) { return this; }
    public ViewPropertyAnimator setDuration(long duration) { return this; }
    public ViewPropertyAnimator setInterpolator(android.animation.TimeInterpolator interpolator) { return this; }
    public ViewPropertyAnimator withEndAction(Runnable action) { return this; }
    public void start() { }
}
"""

FILES["android/view/ViewGroup.java"] = """
package android.view;
import android.content.Context;
import android.util.AttributeSet;
public class ViewGroup extends View {
    public ViewGroup(Context context) { super(context); }
    public ViewGroup(Context context, AttributeSet attrs) { super(context, attrs); }
    public ViewGroup(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public void addView(View child) { }
    public void addView(View child, LayoutParams params) { }
    public void addView(View child, int index, LayoutParams params) { }
    public void removeView(View child) { }
    public void removeViewAt(int index) { }
    public void removeAllViews() { }
    public int getChildCount() { return 0; }
    public View getChildAt(int index) { return null; }
    public void setClipToPadding(boolean clip) { }
    public void setGravity(int gravity) { }
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public static final int FILL_PARENT = -1;
        public int width;
        public int height;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(LayoutParams source) { }
    }
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public MarginLayoutParams(int w, int h) { super(w, h); }
        public void setMargins(int l, int t, int r, int b) { }
    }
}
"""

FILES["android/view/Window.java"] = """
package android.view;
import android.graphics.drawable.Drawable;
public class Window {
    public static final int FEATURE_NO_TITLE = 1;
    public void setBackgroundDrawable(Drawable drawable) { }
    public void addFlags(int flags) { }
    public void clearFlags(int flags) { }
    public void setLayout(int width, int height) { }
    public void setAttributes(WindowManager.LayoutParams params) { }
    public WindowManager.LayoutParams getAttributes() { return null; }
    public void setSoftInputMode(int mode) { }
    public void setStatusBarColor(int color) { }
    public void setNavigationBarColor(int color) { }
    public View getDecorView() { return null; }
}
"""

FILES["android/view/WindowManager.java"] = """
package android.view;
public interface WindowManager {
    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_DIM_BEHIND = 2;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = -2147483648;
        public static final int FLAG_TRANSLUCENT_STATUS = 1024;
        public static final int SOFT_INPUT_STATE_VISIBLE = 4;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 16;
        public float dimAmount = 1f;
        public int gravity;
        public LayoutParams() { super(0, 0); }
        public LayoutParams(int w, int h) { super(w, h); }
    }
}
"""

FILES["android/view/WindowInsets.java"] = """
package android.view;
import android.graphics.Insets;
public class WindowInsets {
    public static final class Type {
        public static int systemBars() { return 0; }
        public static int statusBars() { return 0; }
    }
    public Insets getInsets(int type) { return Insets.of(0, 0, 0, 0); }
    public int getSystemWindowInsetTop() { return 0; }
    public int getSystemWindowInsetBottom() { return 0; }
}
"""

FILES["android/view/animation/DecelerateInterpolator.java"] = """
package android.view.animation;
public class DecelerateInterpolator implements android.animation.TimeInterpolator {
    public DecelerateInterpolator() { }
    public DecelerateInterpolator(float factor) { }
    public float getInterpolation(float input) { return input; }
}
"""

FILES["android/animation/TimeInterpolator.java"] = """
package android.animation;
public interface TimeInterpolator {
    float getInterpolation(float input);
}
"""

FILES["android/animation/ValueAnimator.java"] = """
package android.animation;
public class ValueAnimator {
    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animation);
    }
    public static ValueAnimator ofFloat(float... values) { return null; }
    public ValueAnimator setDuration(long duration) { return this; }
    public ValueAnimator setInterpolator(TimeInterpolator interpolator) { return this; }
    public void addUpdateListener(AnimatorUpdateListener listener) { }
    public void start() { }
    public void cancel() { }
    public Object getAnimatedValue() { return 0f; }
}
"""

FILES["android/widget/TextView.java"] = """
package android.widget;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
public class TextView extends View {
    public TextView(Context context) { super(context); }
    public void setText(CharSequence text) { }
    public void setText(int resource) { }
    public CharSequence getText() { return ""; }
    public void setTextColor(int color) { }
    public void setTextSize(float size) { }
    public void setTextSize(int unit, float size) { }
    public void setTypeface(Typeface typeface) { }
    public void setTypeface(Typeface typeface, int style) { }
    public void setGravity(int gravity) { }
    public void setMaxLines(int lines) { }
    public void setSingleLine(boolean single) { }
    public void setLetterSpacing(float spacing) { }
    public void setLineSpacing(float add, float mult) { }
    public void setHint(int resource) { }
    public void setHint(CharSequence hint) { }
    public void setImeOptions(int options) { }
}
"""

FILES["android/widget/Button.java"] = """
package android.widget;
import android.content.Context;
public class Button extends TextView {
    public Button(Context context) { super(context); }
}
"""

FILES["android/widget/EditText.java"] = """
package android.widget;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
public class EditText extends TextView {
    public EditText(Context context) { super(context); }
    public Editable getText() { return null; }
    public void setText(CharSequence text) { }
    public void setInputType(int type) { }
    public void setSelection(int index) { }
    public void addTextChangedListener(TextWatcher watcher) { }
}
"""

FILES["android/widget/ImageView.java"] = """
package android.widget;
import android.content.Context;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
public class ImageView extends android.view.View {
    public ImageView(Context context) { super(context); }
    public ImageView(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setImageResource(int resource) { }
    public void setColorFilter(int color) { }
    public void setColorFilter(int color, PorterDuff.Mode mode) { }
    public void setScaleType(ScaleType type) { }
    public void setImageAlpha(int alpha) { }
    public enum ScaleType { FIT_XY, CENTER_CROP, CENTER_INSIDE, FIT_CENTER }
}
"""

FILES["android/widget/ProgressBar.java"] = """
package android.widget;
import android.content.Context;
public class ProgressBar extends android.view.View {
    public ProgressBar(Context context) { super(context); }
    public void setProgress(int progress) { }
    public int getProgress() { return 0; }
    public void setMax(int max) { }
    public void setIndeterminate(boolean indeterminate) { }
}
"""

FILES["android/widget/LinearLayout.java"] = """
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public LinearLayout(Context context) { super(context); }
    public LinearLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public LinearLayout(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public void setOrientation(int orientation) { }
    public int getOrientation() { return 0; }
    public void setWeightSum(float sum) { }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;
        public int gravity;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float weight) { super(w, h); this.weight = weight; }
    }
}
"""

FILES["android/widget/FrameLayout.java"] = """
package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) { super(context); }
    public void setForeground(android.graphics.drawable.Drawable d) { }
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int gravity;
        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, int gravity) { super(w, h); this.gravity = gravity; }
    }
}
"""

FILES["android/widget/ScrollView.java"] = """
package android.widget;
import android.content.Context;
public class ScrollView extends FrameLayout {
    public ScrollView(Context context) { super(context); }
    public void setFillViewport(boolean fill) { }
    public void setScrollbars(int bars) { }
}
"""

FILES["android/widget/CompoundButton.java"] = """
package android.widget;
import android.content.Context;
public class CompoundButton extends Button {
    public CompoundButton(Context context) { super(context); }
    public void setChecked(boolean checked) { }
    public boolean isChecked() { return false; }
}
"""

FILES["android/Manifest.java"] = """
package android;
public final class Manifest {
    public static final class permission {
        public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
        public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
        public static final String INTERNET = "android.permission.INTERNET";
    }
}
"""

FILES["android/R.java"] = """
package android;
public final class R {
    public static final class id {
        public static final int content = 0x01020002;
    }
}
"""

# ------------------------------------------------------------------- androidx

FILES["androidx/annotation/NonNull.java"] = """
package androidx.annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface NonNull { }
"""

FILES["androidx/appcompat/app/AppCompatActivity.java"] = """
package androidx.appcompat.app;
public class AppCompatActivity extends android.app.Activity {
}
"""

FILES["androidx/appcompat/widget/AppCompatButton.java"] = """
package androidx.appcompat.widget;
import android.content.Context;
public class AppCompatButton extends android.widget.Button {
    public AppCompatButton(Context context) { super(context); }
}
"""

FILES["androidx/core/app/ActivityCompat.java"] = """
package androidx.core.app;
public class ActivityCompat {
    public static void requestPermissions(android.app.Activity activity, String[] permissions, int code) { }
}
"""

FILES["androidx/core/content/ContextCompat.java"] = """
package androidx.core.content;
import android.content.Context;
public class ContextCompat {
    public static int getColor(Context context, int id) { return 0; }
    public static int checkSelfPermission(Context context, String permission) { return 0; }
}
"""

FILES["androidx/core/content/FileProvider.java"] = """
package androidx.core.content;
import android.content.Context;
import android.net.Uri;
import java.io.File;
public class FileProvider {
    public static Uri getUriForFile(Context context, String authority, File file) { return null; }
}
"""

FILES["androidx/core/widget/NestedScrollView.java"] = """
package androidx.core.widget;
import android.content.Context;
public class NestedScrollView extends android.widget.FrameLayout {
    public NestedScrollView(Context context) { super(context); }
}
"""

FILES["androidx/recyclerview/widget/RecyclerView.java"] = """
package androidx.recyclerview.widget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
public class RecyclerView extends ViewGroup {
    public RecyclerView(Context context) { super(context); }
    public void setLayoutManager(LayoutManager manager) { }
    public void setAdapter(Adapter adapter) { }
    public void setHasFixedSize(boolean fixed) { }
    public void setNestedScrollingEnabled(boolean enabled) { }
    public static abstract class LayoutManager { }
    public static abstract class ViewHolder {
        public final View itemView;
        public ViewHolder(View view) { itemView = view; }
        public int getAdapterPosition() { return 0; }
        public int getPosition() { return 0; }
    }
    public static abstract class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);
        public abstract void onBindViewHolder(VH holder, int position);
        public abstract int getItemCount();
        public final void notifyDataSetChanged() { }
        public final void notifyItemChanged(int position) { }
    }
}
"""

FILES["androidx/recyclerview/widget/LinearLayoutManager.java"] = """
package androidx.recyclerview.widget;
import android.content.Context;
public class LinearLayoutManager extends RecyclerView.LayoutManager {
    public LinearLayoutManager(Context context) { }
    public LinearLayoutManager(Context context, int orientation, boolean reverse) { }
}
"""

# ------------------------------------------------------------------- material

FILES["com/google/android/material/textfield/TextInputLayout.java"] = """
package com.google.android.material.textfield;
import android.content.Context;
import android.widget.LinearLayout;
public class TextInputLayout extends LinearLayout {
    public TextInputLayout(Context context) { super(context); }
    public void setHint(CharSequence hint) { }
    public void setError(CharSequence error) { }
    public void setErrorEnabled(boolean enabled) { }
    public TextInputEditText getEditText() { return null; }
}
"""

FILES["com/google/android/material/textfield/TextInputEditText.java"] = """
package com.google.android.material.textfield;
import android.content.Context;
public class TextInputEditText extends android.widget.EditText {
    public TextInputEditText(Context context) { super(context); }
}
"""

FILES["com/google/android/material/switchmaterial/SwitchMaterial.java"] = """
package com.google.android.material.switchmaterial;
import android.content.Context;
public class SwitchMaterial extends android.widget.CompoundButton {
    public SwitchMaterial(Context context) { super(context); }
}
"""

# ---------------------------------------------------------------------- json

FILES["org/json/JSONObject.java"] = """
package org.json;
public class JSONObject {
    public JSONObject(String json) throws JSONException { }
    public int optInt(String name, int fallback) { return fallback; }
    public long optLong(String name, long fallback) { return fallback; }
    public String optString(String name, String fallback) { return fallback; }
    public boolean optBoolean(String name, boolean fallback) { return fallback; }
}
"""

FILES["org/json/JSONException.java"] = """
package org.json;
public class JSONException extends Exception {
    public JSONException(String message) { super(message); }
}
"""


def main():
    for path, content in FILES.items():
        full = os.path.join(OUT, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as handle:
            handle.write(content.lstrip())
    print("wrote %d stub files to %s" % (len(FILES), OUT))


if __name__ == "__main__":
    main()
