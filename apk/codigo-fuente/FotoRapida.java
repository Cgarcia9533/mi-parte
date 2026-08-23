package com.miparte.montajes;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Foto rapida del widget. Abre la camara, y al aceptar la foto vuelve a
 * abrirla, para disparar en rafaga sin salir de la camara. Se acaba cuando le
 * das a atras, y todas entran en el parte.
 *
 * Las fotos NO entran en el momento: la app web esta cerrada y es ella la que
 * guarda. Se quedan en la cola y entran solas la proxima vez que abres la app.
 */
public class FotoRapida extends Activity {

    private static final int RC = 21;
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
            String b64 = leeB64(destino);
            if (b64 != null) {
                WidgetDatos.ponFoto(this, b64, WidgetDatos.horaAhora(), WidgetDatos.hoyKey());
                destino.delete();
            }
            dispara();   // otra foto: te deja en la camara
        } else {
            finish();    // atras: se acabo la rafaga
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
