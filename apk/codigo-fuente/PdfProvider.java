package com.miparte.montajes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;

/** Provider minimo para compartir ficheros de la cache sin depender de androidx. */
public class PdfProvider extends ContentProvider {
    public static final String AUTORIDAD = "com.miparte.montajes.pdf";

    private File raiz() {
        return new File(getContext().getCacheDir(), "compartir");
    }

    /** Resuelve la uri a un fichero dentro de cache/compartir, sin escapes. */
    private File resuelve(Uri uri) {
        String nombre = uri.getLastPathSegment();
        if (nombre == null) return null;
        File base = raiz();
        File f = new File(base, nombre);
        try {
            if (!f.getCanonicalPath().startsWith(base.getCanonicalPath() + File.separator)) return null;
        } catch (Exception e) { return null; }
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String orden) {
        File f = resuelve(uri);
        if (f == null || !f.exists()) return null;
        String[] cols = (proj != null) ? proj : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        Object[] fila = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) fila[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) fila[i] = f.length();
            else fila[i] = null;
        }
        MatrixCursor c = new MatrixCursor(cols, 1);
        c.addRow(fila);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (n != null && n.toLowerCase().endsWith(".pdf")) return "application/pdf";
        if (n != null && (n.toLowerCase().endsWith(".jpg") || n.toLowerCase().endsWith(".jpeg"))) return "image/jpeg";
        return "application/octet-stream";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String modo) throws java.io.FileNotFoundException {
        File f = resuelve(uri);
        if (f == null) throw new java.io.FileNotFoundException(String.valueOf(uri));
        int m = ParcelFileDescriptor.MODE_READ_ONLY;
        if (modo != null && modo.contains("w")) {
            m = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
            File p = f.getParentFile();
            if (p != null) p.mkdirs();
        }
        return ParcelFileDescriptor.open(f, m);
    }

    public static Uri uriDe(String nombre) {
        return new Uri.Builder().scheme("content").authority(AUTORIDAD).appendPath(nombre).build();
    }

    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
