package com.belopus.life.storage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class ExportProvider extends ContentProvider {

    public static final String AUTHORITY = "com.belopus.life.export";

    public static File getExportsDir(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "exports");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static Uri uriForFile(String fileName) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(fileName)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "application/octet-stream";
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".rle") || lower.endsWith(".cells") || lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("No context");
        String seg = uri.getLastPathSegment();
        if (seg == null || seg.contains("/") || seg.contains("..")) {
            throw new FileNotFoundException("Invalid path");
        }
        File file = new File(getExportsDir(ctx), seg);
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context ctx = getContext();
        if (ctx == null) return null;
        String seg = uri.getLastPathSegment();
        if (seg == null) return null;
        File file = new File(getExportsDir(ctx), seg);
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor mc = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = seg;
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.exists() ? file.length() : 0L;
            else row[i] = null;
        }
        mc.addRow(row);
        return mc;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
