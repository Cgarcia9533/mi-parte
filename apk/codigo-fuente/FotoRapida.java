package com.miparte.montajes;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Foto rapida del widget. Abre la camara, y al aceptar la foto vuelve a
 * abrirla, para disparar en rafaga sin salir de la camara. Se acaba cuando le
 * das a atras, y todas entran en el parte.
 *
 * Las fotos NO entran en el momento: la app web esta cerrada y es ella la que
 * guarda. Se quedan en la cola y entran solas la proxima vez que abres la app.
 *
 * 1.3.4 — LO QUE FALLABA
 * Esta clase mandaba el JPEG TAL CUAL salia de la camara, en base64. Con la
 * camara a 200 MP eso son unos 29 MB por foto, y como el base64 engorda un
 * tercio, cada foto ocupaba en la app mas que muchas apps enteras. A la
 * segunda o la tercera, la app no podia guardar y sacaba «memoria del movil
 * llena», aunque el movil tuviera sitio de sobra: lo que se llenaba era el
 * hueco de la app, y sobre todo la memoria de trabajo al copiar ese pegote
 * cada vez que se guarda.
 *
 * Lo llamativo es que la propia app YA hacia lo correcto por el otro camino:
 * cuando metes una foto desde dentro (addFoto), la reduce a 2000 px y la
 * recomprime al 82% antes de guardarla. Era el atajo del widget el que se
 * saltaba ese paso. Ahora hace lo mismo, en Java y antes de que el pegote
 * llegue a ninguna parte: unos 0,4 MB por foto en vez de 29.
 *
 * Y el original ya no se tira: se guarda en la galeria del movil, album
 * «Mi Parte». Antes se borraba sin mas.
 */
public class FotoRapida extends Activity {

    private static final int RC = 21;

    /** Lo mismo que usa addFoto dentro de la app, para que no haya dos criterios. */
    private static final int LADO = 2000;
    private static final int CALIDAD = 82;

    /** Si la reduccion falla y la foto es pequeña, se manda tal cual. Si es
     *  grande NO se manda: volveriamos al problema, y el original ya esta a
     *  salvo en la galeria. */
    private static final long TOPE_CRUDO = 2L * 1024 * 1024;

    private File destino;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b == null) dispara();
    }

    private void dispara() {
        try {
            File dir = new File(getCacheDir(), "compartir");
            dir.mkdirs();
            destino = new File(dir, "rapida-" + System.currentTimeMillis() + ".jpg");
            destino.createNewFile();
            Uri u = PdfProvider.uriDe(destino.getName());
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, u);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) == null) { finish(); return; }
            startActivityForResult(i, RC);
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req != RC) { super.onActivityResult(req, res, data); return; }
        if (res == RESULT_OK && destino != null && destino.length() > 0) {
            // La hora se coge AHORA, no cuando termine de procesar.
            final Context c = getApplicationContext();
            final File f = destino;
            final String hora = WidgetDatos.horaAhora();
            final String dia = WidgetDatos.hoyKey();
            destino = null;
            // Fuera del hilo de la pantalla: decodificar 200 MP y copiar el
            // original tarda, y aqui colgaria la camara.
            new Thread(new Runnable() {
                public void run() { procesa(c, f, hora, dia); }
            }).start();
            dispara();   // otra foto: te deja en la camara, sin esperar
        } else {
            finish();    // atras: se acabo la rafaga
        }
    }

    private static void procesa(Context c, File f, String hora, String dia) {
        try {
            guardaEnGaleria(c, f);          // el original, entero, a la galeria
            String b64 = reduce(f);         // la copia ligera, para el parte
            if (b64 == null && f.length() <= TOPE_CRUDO) b64 = leeB64(f);
            if (b64 != null) {
                WidgetDatos.ponFoto(c, b64, hora, dia);
                Widgets.refresca(c);
            }
        } catch (Throwable ignored) {
        } finally {
            try { f.delete(); } catch (Throwable ignored) { }
        }
    }

    /** Reduce a LADO px de lado mayor y recomprime. Devuelve base64 o null.
     *
     *  Se decodifica con inSampleSize para no montar nunca el bitmap entero:
     *  200 MP en memoria serian 800 MB y el movil se cae antes de empezar. */
    private static String reduce(File f) {
        Bitmap bm = null;
        try {
            String ruta = f.getAbsolutePath();
            BitmapFactory.Options med = new BitmapFactory.Options();
            med.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(ruta, med);
            int mayor = Math.max(med.outWidth, med.outHeight);
            if (mayor <= 0) return null;

            int muestra = 1;
            while (mayor / (muestra * 2) >= LADO) muestra *= 2;

            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inSampleSize = muestra;
            bm = BitmapFactory.decodeFile(ruta, op);
            if (bm == null) return null;

            // El giro va en el EXIF del original. Al recomprimir se pierde, asi
            // que hay que aplicarlo a mano o las fotos salen tumbadas.
            int giro = giroExif(ruta);
            float k = Math.min(1f, (float) LADO / Math.max(bm.getWidth(), bm.getHeight()));
            if (k < 1f || giro != 0) {
                Matrix m = new Matrix();
                if (k < 1f) m.postScale(k, k);
                if (giro != 0) m.postRotate(giro);
                Bitmap n = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                if (n != bm) { bm.recycle(); bm = n; }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, CALIDAD, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        } finally {
            if (bm != null) try { bm.recycle(); } catch (Throwable ignored) { }
        }
    }

    private static int giroExif(String ruta) {
        try {
            ExifInterface e = new ExifInterface(ruta);
            switch (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_ROTATE_90:  return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** El original, entero y sin tocar, al album «Mi Parte» de la galeria.
     *
     *  Por MediaStore, que en Android 10 y siguientes no pide ningun permiso.
     *  En moviles mas viejos haria falta permiso de almacenamiento y no vale
     *  la pena: ahi no hay camaras de 200 MP. */
    private static void guardaEnGaleria(Context c, File f) {
        if (Build.VERSION.SDK_INT < 29) return;
        OutputStream out = null;
        InputStream in = null;
        Uri u = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "mi-parte-" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Mi Parte");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            u = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) return;
            out = c.getContentResolver().openOutputStream(u);
            if (out == null) return;
            in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            out.close();
            out = null;
            ContentValues fin = new ContentValues();
            fin.put(MediaStore.Images.Media.IS_PENDING, 0);
            c.getContentResolver().update(u, fin, null, null);
            u = null;
        } catch (Throwable ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
            // Si se quedo a medias, no dejar una entrada fantasma en la galeria.
            if (u != null) try { c.getContentResolver().delete(u, null, null); } catch (Throwable ignored) { }
        }
    }

    private static String leeB64(File f) {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) { }
        }
    }
}
