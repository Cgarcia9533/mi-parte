package com.miparte.montajes;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.content.res.Configuration;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {

    /** Origen local. Es https, asi que el documento es un contexto seguro y
     *  las blob: URL que crea la app heredan un origen real: sin esto,
     *  cargando desde file://, localStorage lanzaria SecurityError. */
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/";

    /** Fuente del movil. El WebView es Chromium y no resuelve por nombre ninguna
     *  fuente local: se ha comprobado en un Fold con Android 17 que de 623
     *  nombres solo respondian los alias genericos. Ni «Roboto» ni «Noto Sans»,
     *  estando las dos en /system/fonts. Asi que no se pide por nombre: se saca
     *  el .ttf del APK de la FlipFont y se le sirve al WebView como fuente web. */
    private static final String PAQUETE_FUENTE = "com.monotype.android.font.samsungone";
    private static final String RUTA_FUENTE = "assets/fonts/SamsungOneUI-Regular.ttf";
    private static final String URL_FUENTE = "fuente-movil.ttf";
    private static final String PILA_SANS =
            "system-ui,-apple-system,'Segoe UI',Roboto,sans-serif";

    private byte[] fuenteCache;
    private boolean fuenteBuscada;
    private String fuenteNombre = "";
    private String fuenteOrigen = "";

    private static final int RC_ARCHIVO = 11;
    private static final int RC_NOTIF = 12;
    private static final int RC_ESCRITURA = 13;
    private static final int RC_CARPETA = 14;
    private static final int RC_DICTADO = 15;

    private WebView web;
    private ValueCallback<Uri[]> ficheroCb;
    private Uri fotoUri;
    private String pendienteB64, pendienteNombre;
    private volatile String odB64, odNombre, odSub;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Avisos.creaCanal(this);
        Alarms.reprograma(this);

        web = new WebView(this);
        setContentView(web);
        bordeABorde();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(false);
        web.setBackgroundColor(0xFF23231F);
        WebView.setWebContentsDebuggingEnabled(false);

        web.addJavascriptInterface(new Puente(), "MiParteNative");
        web.setWebViewClient(new Cliente());
        web.setWebChromeClient(new Cromo());

        accionPendiente = b == null && getIntent() != null ? getIntent().getStringExtra("accion") : null;

        web.loadUrl(BASE + "index.html");
    }

    /** Apuntando a Android 16 (API 36) el sistema dibuja la app POR DEBAJO de
     *  la barra de estado y de la de navegacion, y ya no se puede decir que no.
     *
     *  DOS INTENTOS FALLIDOS Y POR QUE:
     *  La 1.6.0 pedia las medidas al sistema. La 1.6.1 añadia medirlas de los
     *  recursos de Android. Ninguna de las dos aparto nada, y el motivo no era
     *  el metodo: era que TODO estaba dentro de un mismo try con un catch que
     *  se come el error sin decir nada, y el margen se aplicaba al final. Si
     *  algo de las primeras lineas petaba —y ahi habia dos llamadas que en
     *  Android 16 ya no valen, setStatusBarColor y setNavigationBarColor— se
     *  saltaba todo lo demas y no quedaba ni rastro de que hubiera fallado.
     *
     *  Ahora cada cosa va en su propio try. Lo PRIMERO que se hace, antes que
     *  nada que pueda petar, es apartar el WebView. Si lo de despues falla,
     *  como mucho se quedan los iconos del reloj de un color raro, pero la app
     *  no se queda tapada. */
    private void bordeABorde() {
        // El hueco de las barras lo pone ahora la propia pagina, con la
        // variable de CSS que se le inyecta al servirla. Aqui ya no se toca el
        // margen de la vista: tres intentos por ahi no movieron nada.
        try { fondoDeVentana(); } catch (Throwable ignored) { }
        try { iconosDeLasBarras(); } catch (Throwable ignored) { }
        try { escuchaAlSistema(); } catch (Throwable ignored) { }
    }

    private void fondoDeVentana() {
        // El MISMO tono que la cabecera y la barra de pestañas de la app, en
        // los dos modos. Antes era el color del papel: en modo claro dejaba
        // una franja clara pegada a unas barras oscuras y cantaba.
        int fondo = WidgetDatos.color(this, R.color.w_barra_app);
        getWindow().setBackgroundDrawable(new ColorDrawable(fondo));
        if (web != null) web.setBackgroundColor(fondo);
    }

    /** Escucha por si el sistema manda las medidas de verdad, que son mejores
     *  en apaisado y con muesca. Si no manda nada, no pasa nada: ya esta
     *  puesto el margen medido de los recursos. */
    private void escuchaAlSistema() {
        web.setFitsSystemWindows(false);
        View.OnApplyWindowInsetsListener l = new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                aplica(in);
                return in;
            }
        };
        web.setOnApplyWindowInsetsListener(l);
        getWindow().getDecorView().setOnApplyWindowInsetsListener(
                new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                        aplica(in);
                        return v.onApplyWindowInsets(in);
                    }
                });
        web.requestApplyInsets();
    }

    /** Aparta el WebView con lo que diga el sistema.
     *
     *  Solo pisa el margen de respaldo si trae ALGO por arriba o por abajo. Si
     *  viniera medio vacio, se quedaria peor de lo que ya estaba. */
    private void aplica(WindowInsets in) {
        try {
            if (in == null || web == null) return;
            int iz, ar, de, ab;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = in.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                iz = b.left; ar = b.top; de = b.right; ab = b.bottom;
            } else {
                // «Stable» y no «SystemWindow» a proposito: el teclado NO cuenta
                // como barra. Con adjustResize, getSystemWindowInsetBottom crece
                // hasta la altura del teclado y la pagina se quedaba con un
                // hueco enorme debajo mientras se escribe.
                iz = in.getStableInsetLeft();
                ar = in.getStableInsetTop();
                de = in.getStableInsetRight();
                ab = in.getStableInsetBottom();
            }
            int tec = altoDelTeclado(in);
            if (tec > 0) ab = 0;   // el teclado ya tapa la barra de abajo
            if (ar <= 0 && ab <= 0 && iz <= 0 && de <= 0 && tec <= 0) return;
            // Las medidas de verdad, a la pagina. En pixeles de CSS, no fisicos.
            float d = getResources().getDisplayMetrics().density;
            if (d <= 0) d = 1f;
            final String css = ":root{--bar-arriba:" + Math.round(ar / d)
                    + "px;--bar-abajo:" + Math.round(ab / d)
                    + "px;--bar-izq:" + Math.round(iz / d)
                    + "px;--bar-der:" + Math.round(de / d)
                    + "px;--teclado:" + Math.round(tec / d) + "px}";
            web.evaluateJavascript(
                    "(function(){var s=document.getElementById('miparte-barras');"
                  + "if(!s){s=document.createElement('style');s.id='miparte-barras';"
                  + "(document.head||document.documentElement).appendChild(s);}"
                  + "s.textContent=" + jsTxt(css) + ";})()", null);
            if (tec != tecladoAnterior) avisaDelTeclado();
            tecladoAnterior = tec;
        } catch (Throwable ignored) { }
    }

    /** Cuanto tapa el teclado, en pixeles fisicos. 0 si no esta.
     *
     *  Desde Android 15, una app que apunta a SDK 35 o mas ya NO se redimensiona
     *  sola cuando sale el teclado: «adjustResize» dejo de tener efecto y el
     *  teclado se pinta ENCIMA de lo que estas escribiendo. Hay que preguntarle
     *  al sistema cuanto ocupa y decirselo a la pagina, igual que con las
     *  barras. Por debajo de Android 11 no hay forma de preguntarlo, pero es que
     *  alli adjustResize todavia funciona y no hace falta. */
    private int altoDelTeclado(WindowInsets in) {
        // OJO CON LA VERSION: por debajo de Android 15 «adjustResize» SI funciona
        // y la ventana ya encoge sola. Si ademas le diesemos el hueco a la
        // pagina, se apartaria dos veces y quedaria una franja muerta enorme.
        if (Build.VERSION.SDK_INT < 35) return 0;
        try { return in.getInsets(WindowInsets.Type.ime()).bottom; }
        catch (Throwable t) { return 0; }
    }

    /** Avisa a la pagina de que el teclado ha cambiado.
     *
     *  NO se hace aqui el scrollIntoView del navegador: no sirve. La ventana no
     *  encoge, asi que el navegador mide contra la pantalla entera, ve el campo
     *  dentro y decide que ya se ve. Quien sabe donde esta la raya del teclado
     *  es la pagina, que tiene la medida en --teclado; alli esta la cuenta
     *  (miparte-native.js, apartado 8). */
    private void avisaDelTeclado() {
        if (web == null) return;
        web.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (web != null) web.evaluateJavascript(
                            "(function(){try{window.dispatchEvent("
                          + "new Event('miparte-teclado'));}catch(e){}})()", null);
                } catch (Throwable ignored) { }
            }
        }, 60);
    }

    private int tecladoAnterior = 0;

    /** El hueco de las barras, en CSS.
     *
     *  Tres intentos apartando el WebView con setPadding no movieron nada en
     *  el movil de Carlos. Asi que se deja de tocar la vista de Android y se le
     *  dice a la PAGINA cuanto hueco tiene que dejar, que es donde vive la
     *  maquetacion: la cabecera y la barra de pestañas son «sticky», y con una
     *  variable de CSS se pegan en el sitio bueno sin pelearse con nadie.
     *
     *  OJO CON LAS UNIDADES: los recursos de Android vienen en pixeles fisicos
     *  y el CSS trabaja en pixeles logicos. Hay que dividir por la densidad de
     *  la pantalla o en un movil como este el hueco saldria del triple. */
    String cssDeLasBarras() {
        int ar = 0, ab = 0;
        try {
            float d = getResources().getDisplayMetrics().density;
            if (d <= 0) d = 1f;
            ar = Math.round(altoDeSistema("status_bar_height") / d);
            ab = Math.round(altoDeSistema("navigation_bar_height") / d);
            boolean apaisado = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
            if (apaisado) ab = 0;   // en apaisado la barra se va a un lado
        } catch (Throwable ignored) { }
        return ":root{--bar-arriba:" + ar + "px;--bar-abajo:" + ab
                + "px;--bar-izq:0px;--bar-der:0px;--teclado:0px}";
    }

    /** Le pasa las medidas nuevas a la pagina, sin recargarla. Al girar. */
    private void refrescaBarras() {
        if (web == null) return;
        final String css = cssDeLasBarras();
        web.evaluateJavascript(
                "(function(){var s=document.getElementById('miparte-barras');"
              + "if(!s){s=document.createElement('style');s.id='miparte-barras';"
              + "(document.head||document.documentElement).appendChild(s);}"
              + "s.textContent=" + jsTxt(css) + ";})()", null);
    }

    private int altoDeSistema(String nombre) {
        try {
            int id = getResources().getIdentifier(nombre, "dimen", "android");
            return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** El reloj, la bateria y los botones del sistema, SIEMPRE en claro.
     *
     *  Antes esto seguia el modo del movil: en modo claro pedia iconos
     *  oscuros. Pero la franja de detras la pinta la cabecera de la app, que
     *  es oscura en los DOS modos. Resultado: en modo claro salia el reloj en
     *  negro sobre negro y no se leia.
     *
     *  Lo que manda no es el modo del telefono, es el color que hay detras. Y
     *  detras siempre hay una barra oscura. */
    private void iconosDeLasBarras() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                      | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                wic.setSystemBarsAppearance(0, m);   // 0 = iconos claros
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            int f = getWindow().getDecorView().getSystemUiVisibility();
            getWindow().getDecorView().setSystemUiVisibility(f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** Al girar el movil o al cambiar a modo claro, las barras cambian de sitio
     *  y de color. El manifest declara estos cambios, asi que la pantalla no se
     *  rehace sola: hay que volver a medir a mano. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration nueva) {
        super.onConfigurationChanged(nueva);
        try { refrescaBarras(); } catch (Throwable ignored) { }
        try { fondoDeVentana(); } catch (Throwable ignored) { }
        try { iconosDeLasBarras(); } catch (Throwable ignored) { }
        try { if (web != null) web.requestApplyInsets(); } catch (Throwable ignored) { }
    }

    // ---------------- servidor de assets ----------------

    private final class Cliente extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (!HOST.equals(u.getHost())) {
                // Nada sale del telefono. La app es offline por diseno.
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
            String ruta = u.getPath() == null ? "/" : u.getPath();
            if (ruta.startsWith("/")) ruta = ruta.substring(1);
            if (ruta.isEmpty()) ruta = "index.html";
            try {
                if ("index.html".equals(ruta)) {
                    byte[] html = inyecta(lee(getAssets().open("index.html")));
                    return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(html));
                }
                if (URL_FUENTE.equals(ruta)) {
                    byte[] tipo = fuenteMovil();
                    if (tipo == null) throw new Exception("sin fuente");
                    return new WebResourceResponse("font/ttf", null, new ByteArrayInputStream(tipo));
                }
                return new WebResourceResponse(mime(ruta), null, getAssets().open(ruta));
            } catch (Exception e) {
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            // La pagina ya existe: es el momento de pasarle lo que traia el widget.
            paginaLista = true;
            sueltaPendientes();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (HOST.equals(u.getHost())) return false;
            // Mapa de la obra y telefonos de contacto: los abre Android, no el WebView.
            abrirFuera(u.toString());
            return true;
        }
    }

    /** Inserta el puente justo detras de <head>, sin tocar index.html en disco. */
    private byte[] inyecta(byte[] html) throws Exception {
        String txt = new String(html, "UTF-8");
        String shim = "<script>" + new String(lee(getAssets().open("miparte-native.js")), "UTF-8") + "</script>";
        // Si la fuente del movil esta disponible, se antepone a la pila sans de
        // la app. La monoespaciada no se toca. index.html en disco queda igual.
        byte[] tipo = fuenteMovil();
        if (tipo != null && txt.contains(PILA_SANS)) {
            txt = txt.replace(PILA_SANS, "'FuenteMovil'," + PILA_SANS);
            // Primero la fuente incrustada en la propia pagina (data:), que no
            // depende de que el WebView acepte la peticion; la ruta servida
            // queda como respaldo.
            String src = "url('/" + URL_FUENTE + "') format('truetype')";
            if (tipo.length < 1500000) {
                String b64 = android.util.Base64.encodeToString(tipo, android.util.Base64.NO_WRAP);
                src = "url(data:font/ttf;base64," + b64 + ") format('truetype')," + src;
            }
            // La regla se pone por JS y se repone si desaparece. Con este
            // index.html un <style> bastaria, pero esto cuesta lo mismo y
            // aguanta si el index vuelve al formato empaquetado (aquel
            // reemplazaba el <html> entero al arrancar y se llevaba el <style>).
            String css = "@font-face{font-family:'FuenteMovil';src:" + src + ";font-display:swap}";
            shim = "<script>(function(){var c=\"" + css + "\";"
                    + "function p(){if(document.getElementById('miparte-fuente'))return;"
                    + "var s=document.createElement('style');s.id='miparte-fuente';"
                    + "s.textContent=c;(document.head||document.documentElement).appendChild(s);}"
                    + "p();"
                    + "try{new MutationObserver(p).observe(document,{childList:true});}catch(e){}"
                    + "document.addEventListener('DOMContentLoaded',p);"
                    + "window.addEventListener('load',p);"
                    + "})();</script>" + shim;
        }
        // Para poder verlo desde la app (Ajustes > Datos y copias) sin logcat.
        shim = "<script>window.MiParteFuente={ok:" + (tipo != null)
                + ",fuente:" + jsTxt(fuenteNombre) + ",origen:" + jsTxt(fuenteOrigen)
                + ",bytes:" + (tipo == null ? 0 : tipo.length) + "};</script>" + shim;
        // El hueco de las barras del movil, lo primero de todo, para que la
        // pagina ya nazca con el sitio reservado y no de un salto al abrirse.
        shim = "<style id=\"miparte-barras\">" + cssDeLasBarras() + "</style>" + shim;
        int i = txt.toLowerCase().indexOf("<head");
        if (i >= 0) {
            int fin = txt.indexOf('>', i);
            if (fin > 0) txt = txt.substring(0, fin + 1) + shim + txt.substring(fin + 1);
        } else {
            txt = shim + txt;
        }
        return txt.getBytes("UTF-8");
    }

    private static String jsTxt(String s) {
        String t = s == null ? "" : s;
        t = t.replace("\\", "").replace("'", "").replace("\n", " ");
        return "'" + t + "'";
    }

    /** Busca una letra del movil por tres vias, en este orden:
     *   1. la FlipFont que el usuario tiene elegida, si el sistema la declara;
     *   2. cualquier paquete de fuente instalado (com.monotype.android.font.*);
     *   3. las fuentes de /system/fonts, por orden de preferencia.
     *  Si todo falla devuelve null y la app se queda con la letra de antes:
     *  nunca revienta por esto. */
    private byte[] fuenteMovil() {
        if (fuenteBuscada) return fuenteCache;
        fuenteBuscada = true;

        java.util.ArrayList<String> paquetes = new java.util.ArrayList<>();
        try {
            String elegida = android.provider.Settings.System.getString(
                    getContentResolver(), "theme_font");
            if (elegida != null && elegida.contains("font")) paquetes.add(elegida.trim());
        } catch (Exception ignored) { }
        if (!paquetes.contains(PAQUETE_FUENTE)) paquetes.add(PAQUETE_FUENTE);
        try {
            java.util.List<ApplicationInfo> todas = getPackageManager().getInstalledApplications(0);
            for (ApplicationInfo ai : todas) {
                if (ai.packageName != null && ai.packageName.contains("android.font")
                        && !paquetes.contains(ai.packageName)) {
                    paquetes.add(ai.packageName);
                }
            }
        } catch (Exception ignored) { }

        for (int i = 0; i < paquetes.size(); i++) {
            byte[] b = deApkFuente(paquetes.get(i));
            if (b != null && b.length > 2000) {
                fuenteCache = b;
                fuenteOrigen = paquetes.get(i);
                return fuenteCache;
            }
        }

        String[] sis = { "SamsungOneUI", "SamsungOne", "SamsungSans", "Roboto-Regular",
                "NotoSans-Regular", "DroidSans" };
        for (int i = 0; i < sis.length; i++) {
            byte[] b = deSystemFonts(sis[i]);
            if (b != null && b.length > 2000) {
                fuenteCache = b;
                fuenteOrigen = "/system/fonts";
                return fuenteCache;
            }
        }
        return null;
    }

    /** Una fuente del sistema cuyo nombre empiece por el prefijo dado. */
    private byte[] deSystemFonts(String prefijo) {
        try {
            java.io.File[] fs = new java.io.File("/system/fonts").listFiles();
            if (fs == null) return null;
            for (int i = 0; i < fs.length; i++) {
                String n = fs[i].getName();
                String bajo = n.toLowerCase();
                if (!bajo.startsWith(prefijo.toLowerCase())) continue;
                if (!bajo.endsWith(".ttf") && !bajo.endsWith(".otf")) continue;
                if (!fs[i].canRead()) continue;
                fuenteNombre = n;
                return lee(new java.io.FileInputStream(fs[i]));
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Saca el .ttf de dentro del APK de una fuente instalada. */
    private byte[] deApkFuente(String paquete) {
        ZipFile z = null;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(paquete, 0);
            z = new ZipFile(ai.sourceDir);
            ZipEntry e = z.getEntry(RUTA_FUENTE);
            if (e == null) {
                // Si Samsung cambia la ruta, vale el primer fichero de fuente que haya.
                ZipEntry alt = null;
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry c = en.nextElement();
                    String n = c.getName().toLowerCase();
                    if (!n.endsWith(".ttf") && !n.endsWith(".otf")) continue;
                    if (n.contains("regular")) { alt = c; break; }
                    if (alt == null) alt = c;
                }
                e = alt;
            }
            if (e != null) {
                fuenteNombre = e.getName();
                return lee(z.getInputStream(e));
            }
        } catch (Exception ignored) {
        } finally {
            if (z != null) try { z.close(); } catch (Exception ignored) { }
        }
        return null;
    }

    private static byte[] lee(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        in.close();
        return o.toByteArray();
    }

    private static String mime(String ruta) {
        String r = ruta.toLowerCase();
        if (r.endsWith(".js")) return "text/javascript";
        if (r.endsWith(".json") || r.endsWith("manifest.json")) return "application/manifest+json";
        if (r.endsWith(".png")) return "image/png";
        if (r.endsWith(".css")) return "text/css";
        if (r.endsWith(".woff2")) return "font/woff2";
        if (r.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    // ---------------- camara y galeria ----------------

    private final class Cromo extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (ficheroCb != null) ficheroCb.onReceiveValue(null);
            ficheroCb = cb;
            fotoUri = null;
            try {
                boolean multi = params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;

                // El campo del HTML dice que quiere: fotos (accept="image/*") o
                // un fichero de copia (accept=".json"). Sin esto salia siempre
                // la galeria y no se podia cargar una copia.
                if (!pideFotos(params)) {
                    Intent doc = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    doc.addCategory(Intent.CATEGORY_OPENABLE);
                    doc.setType("*/*");
                    doc.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/json", "text/json", "text/plain", "application/octet-stream",
                            // el .xlsx de la lista de materiales
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "application/zip"});
                    doc.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
                    startActivityForResult(Intent.createChooser(doc, "Elige el archivo"), RC_ARCHIVO);
                    return true;
                }

                Intent camara = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                boolean hayCamara = camara.resolveActivity(getPackageManager()) != null;
                if (hayCamara) {
                    File dir = new File(getCacheDir(), "compartir");
                    dir.mkdirs();
                    File destino = new File(dir, "foto-" + System.currentTimeMillis() + ".jpg");
                    destino.createNewFile();
                    fotoUri = PdfProvider.uriDe(destino.getName());
                    camara.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);
                    camara.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                // El campo con capture="environment" (el boton de «Foto rapida»
                // del inicio) quiere la camara y nada mas: sin pasar por la
                // pantalla de elegir. La galeria de ese mismo boton va por otro
                // campo, sin capture, que se abre con la pulsacion larga.
                if (hayCamara && params != null && params.isCaptureEnabled()) {
                    startActivityForResult(camara, RC_ARCHIVO);
                    return true;
                }

                Intent galeria = new Intent(Intent.ACTION_GET_CONTENT);
                galeria.addCategory(Intent.CATEGORY_OPENABLE);
                galeria.setType("image/*");
                galeria.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);

                Intent elige = Intent.createChooser(galeria, "Hacer foto o adjuntar");
                if (hayCamara) elige.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camara});
                startActivityForResult(elige, RC_ARCHIVO);
                return true;
            } catch (Exception e) {
                ficheroCb = null;
                fotoUri = null;
                return false;
            }
        }
    }

    /** true si el <input type="file"> pide imagenes (o no dice nada). */
    private boolean pideFotos(WebChromeClient.FileChooserParams params) {
        if (params == null) return true;
        String[] tipos = params.getAcceptTypes();
        if (tipos == null) return true;
        boolean alguno = false;
        for (String t : tipos) {
            if (t == null) continue;
            for (String u : t.split(",")) {
                u = u.trim().toLowerCase();
                if (u.isEmpty()) continue;
                alguno = true;
                if (!u.startsWith("image/") && !u.equals(".jpg") && !u.equals(".jpeg")
                        && !u.equals(".png") && !u.equals(".webp") && !u.equals(".heic")) {
                    return false;
                }
            }
        }
        return true; // todo lo pedido eran imagenes (o no pedia nada)
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == RC_CARPETA) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                Uri arbol = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(arbol,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) { }
                getSharedPreferences("miparte", MODE_PRIVATE).edit()
                        .putString("carpetaOd", arbol.toString()).apply();
                avisaCarpetaJs();
                if (odB64 != null) enSegundoPlano();
            } else {
                odB64 = null;
            }
            return;
        }
        if (req == RC_DICTADO) {
            String dicho = null;
            if (res == RESULT_OK && data != null) {
                ArrayList<String> l = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (l != null && !l.isEmpty()) dicho = l.get(0);
            }
            if (dicho != null && dicho.trim().length() > 0) diceLaPagina(
                    "if(window.miParteDictadoOk)window.miParteDictadoOk(" + jsCad(dicho) + ")");
            else finDictado();
            return;
        }
        if (req != RC_ARCHIVO) { super.onActivityResult(req, res, data); return; }
        Uri[] r = null;
        if (res == RESULT_OK) {
            if (data == null || (data.getData() == null && data.getClipData() == null)) {
                if (fotoUri != null) r = new Uri[]{fotoUri};
            } else if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                List<Uri> l = new ArrayList<>();
                for (int i = 0; i < n; i++) l.add(data.getClipData().getItemAt(i).getUri());
                r = l.toArray(new Uri[0]);
            } else {
                r = new Uri[]{data.getData()};
            }
        }
        if (ficheroCb != null) ficheroCb.onReceiveValue(r);
        ficheroCb = null;
        fotoUri = null;
    }

    // ---------------- dictado por voz ----------------

    /** Abre el reconocedor de voz del sistema y devuelve lo dicho a la pagina.
     *
     *  POR QUE NATIVO Y NO EL DEL NAVEGADOR: el WebView de Android declara
     *  «webkitSpeechRecognition» pero no lo implementa. Chromium deja el
     *  servicio de voz fuera del componente, asi que el objeto existe, el
     *  boton se pinta, y al arrancarlo salta onerror y no pasa nada. Por eso
     *  la pagina pregunta primero por este puente.
     *
     *  POR QUE EL INTENT Y NO SpeechRecognizer: con el intent graba la app de
     *  voz del sistema, no esta. Asi Mi Parte NO necesita el permiso
     *  RECORD_AUDIO, que es de los sensibles y obliga a dar explicaciones en
     *  Play. Lo que se pierde es el dictado continuo: una parrafada por
     *  pulsacion. Para describir un trabajo sobra. */
    private void abreDictado() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            String idioma;
            try { idioma = Locale.getDefault().toLanguageTag(); }
            catch (Throwable ig) { idioma = "es-ES"; }
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, idioma);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el trabajo");
            startActivityForResult(i, RC_DICTADO);
        } catch (Throwable t) {
            // Sin app de voz instalada. Que la pagina apague el microfono
            // encendido, o se queda «escuchando» para siempre.
            finDictado();
            try {
                Toast.makeText(this, "Este telefono no trae dictado por voz",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        }
    }

    private void finDictado() {
        diceLaPagina("if(window.miParteDictadoFin)window.miParteDictadoFin()");
    }

    private void diceLaPagina(final String js) {
        runOnUiThread(new Runnable() {
            public void run() {
                try { if (web != null) web.evaluateJavascript("(function(){" + js + "})()", null); }
                catch (Throwable ignored) { }
            }
        });
    }

    /** Como jsTxt pero SIN perder nada. jsTxt tira las comillas y las barras
     *  para no complicarse; aqui no vale, porque esto es texto del usuario. */
    private static String jsCad(String s) {
        String t = s == null ? "" : s;
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c < 0x20 || c == 0x2028 || c == 0x2029)
                b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    // ---------------- puente JS ----------------

    public final class Puente {

        @JavascriptInterface
        public boolean sharePdf(final String b64, final String nombre, final String tipo) {
            try {
                final File f = escribeCache(b64, limpia(nombre));
                runOnUiThread(new Runnable() {
                    public void run() {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType(tipo == null || tipo.isEmpty() ? "application/pdf" : tipo);
                        i.putExtra(Intent.EXTRA_STREAM, PdfProvider.uriDe(f.getName()));
                        i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "Compartir parte"));
                    }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** Abre el PDF en el lector que tenga el movil, sin dejarlo en Descargas.
         *  Devuelve false si no hay ninguna app capaz de abrirlo; la app web
         *  esconde el boton cuando este metodo no existe. */
        @JavascriptInterface
        public boolean verPdf(final String b64, final String nombre) {
            final File f;
            try {
                f = escribeCache(b64, limpia(nombre));
            } catch (Exception e) {
                return false;
            }
            final Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(PdfProvider.uriDe(f.getName()), "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) == null) return false;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        startActivity(Intent.createChooser(i, "Ver el parte"));
                    } catch (Exception e) {
                        aviso("No hay ninguna app para abrir PDF.");
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public void savePdf(String b64, String nombre) {
            pendienteB64 = b64;
            pendienteNombre = limpia(nombre);
            if (Build.VERSION.SDK_INT < 29
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_ESCRITURA);
                    }
                });
                return;
            }
            guardaEnDescargas();
        }

        /** Carpeta elegida (OneDrive u otra) o cadena vacia si no hay ninguna. */
        @JavascriptInterface
        /** Nombre de la carpeta fija, o cadena vacia si no hay. La app lo
         *  usa para escribir «Va a «Partes 2026»» debajo del boton. */
        public String carpetaOneDrive() {
            Uri a = carpetaOd();
            return a == null ? "" : nombreCarpeta(a);
        }

        @JavascriptInterface
        /** Abre el selector de carpetas de Android. Sirve para Drive, Dropbox
         *  y carpetas locales. OneDrive no sale ahi: su app de Android nunca
         *  implemento el Storage Access Framework. Para OneDrive esta el envio
         *  directo de guardaOneDrive. */
        public void eligeCarpetaOneDrive() { pideCarpeta(); }

        /** Guarda el PDF en la carpeta elegida. La primera vez pide carpeta y
         *  al volver escribe el fichero que estaba pendiente. */
        @JavascriptInterface
        public void guardaOneDrive(String b64, String nombre) {
            // Con carpeta fija se escribe ahi directamente: un toque, sin salir
            // de la app. Es lo unico que da guardado silencioso de verdad.
            if (carpetaOd() != null) {
                odB64 = b64;
                odNombre = limpia(nombre);
                odSub = null;
                enSegundoPlano();
                return;
            }
            // Sin carpeta fija: hoja de compartir de Android, tal cual, sin
            // apuntar a ninguna app. Salen todas las nubes instaladas y Android
            // pone arriba la ultima que usaste. La app no decide ni avisa nada.
            final File f;
            try {
                f = escribeCache(b64, limpia(nombre));
            } catch (Exception e) {
                aviso("No se ha podido preparar el PDF.");
                return;
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/pdf");
                    i.putExtra(Intent.EXTRA_STREAM, PdfProvider.uriDe(f.getName()));
                    i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        // Sin createChooser: si solo hay una app capaz, Android
                        // va directo; si hay varias, saca su hoja del sistema.
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            startActivity(Intent.createChooser(i, null));
                        } catch (Exception ex) {
                            aviso("No hay ninguna app para recibir el PDF.");
                        }
                    }
                }
            });
        }

        /** Copia de seguridad: va a la subcarpeta «Copias» de la carpeta elegida. */
        @JavascriptInterface
        public void guardaCopia(String b64, String nombre) {
            odB64 = b64;
            odNombre = limpia(nombre);
            odSub = "Copias";
            if (carpetaOd() == null) { pideCarpeta(); return; }
            escribeEnCarpeta();
        }

        /** Tamaño de letra que tiene puesto el telefono: 1.0 = normal, 1.3 = grande. */
        @JavascriptInterface
        public float escalaSistema() {
            try { return getResources().getConfiguration().fontScale; } catch (Exception e) { return 1f; }
        }

        /** Escala el texto de la app. 100 = como estaba. Sin tope por arriba. */
        @JavascriptInterface
        public void ponZoom(final int pct) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try { web.getSettings().setTextZoom(Math.max(50, pct)); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void notify(String titulo, String texto) {
            Avisos.muestra(MainActivity.this, titulo, texto);
        }

        @JavascriptInterface
        public String notifPermission() {
            if (Build.VERSION.SDK_INT < 33) return "granted";
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    ? "granted" : (pedido ? "denied" : "default");
        }

        @JavascriptInterface
        public void requestNotif() {
            if (Build.VERSION.SDK_INT < 33) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    pedido = true;
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_NOTIF);
                }
            });
        }

        @JavascriptInterface
        public void syncAviso(boolean activo, String hora, String diaCerrado) {
            Prefs.guarda(MainActivity.this, activo, hora, diaCerrado);
            Alarms.reprograma(MainActivity.this);
        }

        /** Todo lo que puede impedir que el aviso llegue, para enseñarlo en
         *  Ajustes sin logcat. JSON plano; si algo falla, cadena vacia. */
        @JavascriptInterface
        public String estadoAvisos() {
            boolean permiso = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            boolean canal = true;
            try {
                android.app.NotificationManager nm =
                        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    if (Build.VERSION.SDK_INT >= 24) canal = nm.areNotificationsEnabled();
                    if (canal && Build.VERSION.SDK_INT >= 26) {
                        android.app.NotificationChannel ch = nm.getNotificationChannel(Avisos.CANAL);
                        if (ch != null && ch.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) canal = false;
                    }
                }
            } catch (Exception ignored) { }
            boolean bateria = true;
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    bateria = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
                }
            } catch (Exception ignored) { }
            return "{\"nativo\":true,\"permiso\":" + permiso
                    + ",\"canal\":" + canal
                    + ",\"exactas\":" + Alarms.exactas(MainActivity.this)
                    + ",\"bateria\":" + bateria
                    + ",\"proxima\":" + Prefs.proxima(MainActivity.this) + "}";
        }

        /** Android 12+: pantalla de «Alarmas y recordatorios» de esta app. */
        @JavascriptInterface
        public void pideExacto() {
            if (Build.VERSION.SDK_INT < 31) return;
            abre(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                    Uri.parse("package:" + getPackageName())));
        }

        /** Sacar la app del ahorro de bateria: es lo que aplaza o come la alarma. */
        @JavascriptInterface
        public void pideBateria() {
            if (Build.VERSION.SDK_INT < 23) return;
            abre(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    Uri.parse("package:" + getPackageName())));
        }

        /** Ajustes de notificaciones de la app, por si el canal esta silenciado. */
        @JavascriptInterface
        public void ajustesNotif() {
            Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
            i.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
            i.putExtra("app_package", getPackageName());
            i.putExtra("app_uid", getApplicationInfo().uid);
            abre(i);
        }

        /** Aviso de prueba por el camino de verdad: alarma del sistema -> receptor. */
        @JavascriptInterface
        public void pruebaAlarma(int segundos) {
            Alarms.prueba(MainActivity.this, segundos);
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        if (pm == null) return;
                        pm.print("Mi Parte", web.createPrintDocumentAdapter("Mi Parte"),
                                new PrintAttributes.Builder().build());
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void abrirFuera(String url) { MainActivity.this.abrirFuera(url); }

        /** La app web manda aqui lo que tiene que pintar el widget cada vez que
         *  guarda: obra, fecha, tareas pendientes y si el parte esta cerrado.
         *  El widget vive fuera del WebView y no puede leer localStorage, asi
         *  que esta es la unica via. */
        @JavascriptInterface
        public void syncWidget(String json) {
            WidgetDatos.guarda(MainActivity.this, json);
            Widgets.refresca(MainActivity.this);
            // La hora limite llega en ese json: si ha cambiado, la alarma que
            // repinta el rojo tiene que moverse con ella.
            Alarms.repintaEnLaHora(MainActivity.this);
        }

        /** La pagina llama aqui al pulsar el microfono de «Trabajo realizado».
         *  El numero de linea no hace falta en Java: la pagina se lo guarda en
         *  la funcion que deja puesta en window.miParteDictadoOk. */
        @JavascriptInterface
        public void dictar(int linea) {
            runOnUiThread(new Runnable() {
                public void run() { MainActivity.this.abreDictado(); }
            });
        }

        /** La pagina avisa de que la cola YA ESTA GUARDADA en el disco.
         *
         *  Este es el unico sitio donde se vacia. Antes se vaciaba en cuanto
         *  la pagina decia «me la quedo», pero quedarsela y guardarla no es lo
         *  mismo: la pagina agrupa los guardados 220 ms, y si salias de la app
         *  en ese rato el apunte no llegaba a escribirse y ya no estaba en la
         *  cola. De ahi que unas veces apareciera y otras no. */
        @JavascriptInterface
        public void colaHecha() {
            runOnUiThread(new Runnable() {
                public void run() {
                    String e = colaEntregada;
                    colaEntregada = null;
                    WidgetDatos.quitaEntregado(MainActivity.this, e);
                    Widgets.refresca(MainActivity.this);
                }
            });
        }

        @JavascriptInterface
        public void toast(final String txt) {
            runOnUiThread(new Runnable() {
                public void run() { Toast.makeText(MainActivity.this, txt, Toast.LENGTH_LONG).show(); }
            });
        }
    }

    private boolean pedido = false;

    /** Abre una pantalla de ajustes del sistema; si el movil no la tiene, la
     *  ficha de la app, y si tampoco, avisa en vez de reventar. */
    private void abre(final Intent base) {
        runOnUiThread(new Runnable() {
            public void run() {
                base.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(base);
                    return;
                } catch (Exception ignored) { }
                try {
                    Intent f = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(f);
                } catch (Exception e) {
                    aviso("Este movil no deja abrir esa pantalla desde la app.");
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] permisos, int[] res) {
        if (req == RC_ESCRITURA) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) guardaEnDescargas();
            else aviso("Sin permiso no puedo guardar el PDF. Usa «Compartir».");
        }
        if (req == RC_NOTIF) Alarms.reprograma(this);
    }

    /** Abre un enlace en la app que corresponda (Maps, marcador, navegador).
     *  Lista blanca de esquemas: la ubicacion de la obra la escribe el usuario,
     *  asi que no dejamos pasar intent:, javascript:, file: ni content:. */
    void abrirFuera(final String url) {
        if (url == null) return;
        final String esquema = Uri.parse(url).getScheme();
        if (esquema == null) return;
        String e = esquema.toLowerCase();
        if (!(e.equals("http") || e.equals("https") || e.equals("tel")
                || e.equals("geo") || e.equals("mailto") || e.equals("sms"))) {
            aviso("Ese enlace no se puede abrir.");
            return;
        }
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ex) {
                    aviso("No hay ninguna app en el movil para abrir esto.");
                }
            }
        });
    }

    private static String limpia(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return "parte.pdf";
        String n = nombre.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        return n.isEmpty() ? "parte.pdf" : n;
    }

    private File escribeCache(String b64, String nombre) throws Exception {
        File dir = new File(getCacheDir(), "compartir");
        dir.mkdirs();
        for (File v : dir.listFiles() != null ? dir.listFiles() : new File[0]) {
            if (v.getName().endsWith(".pdf") && v.lastModified() < System.currentTimeMillis() - 3600000L) v.delete();
        }
        File f = new File(dir, nombre);
        FileOutputStream o = new FileOutputStream(f);
        o.write(Base64.decode(b64, Base64.DEFAULT));
        o.close();
        return f;
    }

    // ---------------- carpeta de OneDrive (SAF) ----------------

    /** Arbol guardado, solo si el permiso de escritura sigue vivo. */
    private Uri carpetaOd() {
        String s = getSharedPreferences("miparte", MODE_PRIVATE).getString("carpetaOd", null);
        if (s == null) return null;
        Uri u = Uri.parse(s);
        try {
            for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().equals(u) && p.isWritePermission()) return u;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String nombreCarpeta(Uri arbol) {
        Cursor c = null;
        try {
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(arbol, DocumentsContract.getTreeDocumentId(arbol));
            c = getContentResolver().query(doc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            String n = (c != null && c.moveToFirst()) ? c.getString(0) : null;
            return n == null ? "la carpeta elegida" : n;
        } catch (Exception e) {
            return "la carpeta elegida";
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
    }

    private void pideCarpeta() {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(i, RC_CARPETA);
                } catch (Exception e) {
                    odB64 = null;
                    aviso("Este movil no deja elegir carpeta. Usa «Compartir».");
                }
            }
        });
    }

    private void avisaCarpetaJs() {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    web.evaluateJavascript("window.dispatchEvent(new Event('miparte-carpeta'))", null);
                } catch (Exception ignored) { }
            }
        });
    }

    /** Escribe el PDF pendiente en la carpeta. Si ya existe un parte con ese
     *  nombre lo borra antes, para no acumular «(1)», «(2)»... */
    /** Escribir en un proveedor de nube puede tirar de red: nunca en el hilo
     *  principal, o Android mata la app por ANR. */
    private void enSegundoPlano() {
        new Thread(new Runnable() {
            public void run() { escribeEnCarpeta(); }
        }).start();
    }

    private void escribeEnCarpeta() {
        final String b64 = odB64, nombre = odNombre;
        odB64 = null;
        if (b64 == null) return;
        Uri arbol = carpetaOd();
        if (arbol == null) { aviso("No hay carpeta elegida."); return; }
        final String sub = odSub;
        odSub = null;
        try {
            Uri padre = padreDestino(arbol, sub);
            borraSiExiste(padre, nombre);
            Uri destino = DocumentsContract.createDocument(getContentResolver(), padre,
                    nombre.toLowerCase().endsWith(".json") ? "application/json" : "application/pdf", nombre);
            if (destino == null) throw new Exception("sin destino");
            OutputStream o = getContentResolver().openOutputStream(destino);
            if (o == null) throw new Exception("sin flujo de escritura");
            try {
                o.write(Base64.decode(b64, Base64.DEFAULT));
            } finally {
                try { o.close(); } catch (Exception ignored) { }
            }
            aviso("Guardado en " + nombreCarpeta(arbol) + (sub == null ? "" : "/" + sub) + ": " + nombre);
        } catch (Exception e) {
            aviso("No se ha podido guardar en la carpeta. Vuelve a elegirla o usa «Compartir».");
        }
    }

    /** Carpeta donde escribir: la elegida, o una subcarpeta suya que se crea si no existe. */
    private Uri padreDestino(Uri arbol, String sub) {
        Uri padre = DocumentsContract.buildDocumentUriUsingTree(arbol, DocumentsContract.getTreeDocumentId(arbol));
        if (sub == null || sub.length() == 0) return padre;
        Uri hijo = buscaHijo(padre, sub);
        if (hijo != null) return hijo;
        try {
            Uri nueva = DocumentsContract.createDocument(getContentResolver(), padre,
                    DocumentsContract.Document.MIME_TYPE_DIR, sub);
            return nueva == null ? padre : nueva;
        } catch (Exception e) {
            return padre;
        }
    }

    private Uri buscaHijo(Uri padre, String nombre) {
        Cursor c = null;
        try {
            Uri hijos = DocumentsContract.buildChildDocumentsUriUsingTree(padre, DocumentsContract.getDocumentId(padre));
            c = getContentResolver().query(hijos, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c == null) return null;
            while (c.moveToNext()) {
                if (nombre.equals(c.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(padre, c.getString(0));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
        return null;
    }

    private void borraSiExiste(Uri padre, String nombre) {
        Cursor c = null;
        try {
            Uri hijos = DocumentsContract.buildChildDocumentsUriUsingTree(padre, DocumentsContract.getDocumentId(padre));
            c = getContentResolver().query(hijos, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c == null) return;
            while (c.moveToNext()) {
                if (nombre.equals(c.getString(1))) {
                    DocumentsContract.deleteDocument(getContentResolver(),
                            DocumentsContract.buildDocumentUriUsingTree(padre, c.getString(0)));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
    }

    private void guardaEnDescargas() {
        final String b64 = pendienteB64, nombre = pendienteNombre;
        pendienteB64 = null;
        if (b64 == null) return;
        try {
            byte[] datos = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                cv.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri destino = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (destino == null) throw new Exception("sin destino");
                OutputStream o = getContentResolver().openOutputStream(destino);
                o.write(datos);
                o.close();
                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(destino, cv, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                FileOutputStream o = new FileOutputStream(new File(dir, nombre));
                o.write(datos);
                o.close();
            }
            aviso("PDF guardado en Descargas: " + nombre);
        } catch (Exception e) {
            aviso("No se ha podido guardar el PDF en Descargas.");
        }
    }

    private void aviso(final String txt) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, txt, Toast.LENGTH_LONG).show(); }
        });
    }

    @Override
    public void onBackPressed() {
        // Antes esto era solo canGoBack(), y como la app es una sola pagina que
        // cambia por dentro, el WebView NUNCA tiene historial: canGoBack() daba
        // siempre false y el boton atras cerraba la app estuvieras donde
        // estuvieras. Ahora se le pregunta a la app: si ella sube un escalon,
        // aqui no se hace nada. Solo se cierra si dice que ya esta en inicio.
        if (!paginaLista || web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript(
                "(function(){try{return window.MiParteAtras && window.MiParteAtras() ? 'si' : 'no';}"
                        + "catch(e){return 'no';}})()",
                new ValueCallback<String>() {
                    public void onReceiveValue(String r) {
                        if (r == null || !r.contains("si")) finish();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver de «Alarmas y recordatorios» o del ahorro de bateria hay que
        // rehacer las alarmas: las que se pusieron inexactas pasan a exactas.
        Avisos.creaCanal(this);
        Alarms.reprograma(this);
        // La cola del widget: antes solo se soltaba en arranque en frio y al
        // abrir DESDE un widget. Si la app seguia viva en segundo plano y la
        // traias al frente, no llegaba nunca.
        sueltaPendientes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Alarms.reprograma(this);
        Widgets.refresca(this);
    }

    // ---------------- widget ----------------

    private String accionPendiente;
    private boolean paginaLista;

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (i != null && i.getStringExtra("accion") != null) accionPendiente = i.getStringExtra("accion");
        sueltaPendientes();
    }

    /** Pasa a la app web la cola del widget y la accion pendiente.
     *
     *  window.MiParteCola lo define React en componentDidMount, que corre
     *  DESPUES de onPageFinished: hay que preguntar si el enganche existia y
     *  solo entonces dar la cola por entregada. Si no, se reintenta. */
    private void sueltaPendientes() { sueltaPendientes(0); }

    /** La misma cola que ya se ha mandado y todavia no ha contestado, y cuando
     *  se mando. Sirve para no mandarla dos veces seguidas. Caduca sola a los
     *  tres segundos: si algo va mal, se reintenta, no se queda atascada. */
    private String colaEnVuelo;
    /** Lo ultimo que se entrego a la pagina y aun no ha confirmado guardado. */
    private String colaEntregada;
    private long colaDesde;

    private void sueltaPendientes(final int intento) {
        if (!paginaLista || web == null) return;

        String pendiente = WidgetDatos.recoge(this);
        // Anti doble entrega: al tocar un boton de widget con la app viva,
        // Android llama a onNewIntent Y a onResume, uno detras de otro. Los dos
        // sueltan la cola, y como el acuse de recibo tarda (evaluateJavascript
        // contesta mas tarde), el segundo se encontraba todo igual que el
        // primero y lo mandaba otra vez. De ahi las dos lineas de trabajo.
        if (pendiente != null && pendiente.equals(colaEnVuelo)
                && System.currentTimeMillis() - colaDesde < 3000) {
            pendiente = null;
        }
        final String cola = pendiente;
        final String accion = accionPendiente;
        final boolean hayAccion = accion != null && !accion.isEmpty() && !"foto".equals(accion);
        if (cola == null && !hayAccion) { accionPendiente = null; return; }

        // La accion se coge AQUI, no en el acuse de recibo. Asi la segunda
        // llamada ya no la ve. Si la entrega falla, se devuelve y se reintenta.
        if (hayAccion) accionPendiente = null;
        if (cola != null) { colaEnVuelo = cola; colaDesde = System.currentTimeMillis(); }

        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    if (cola != null) {
                        web.evaluateJavascript(
                                // «si» SOLO si la pagina confirma que se ha
                                // quedado con la cola. Antes bastaba con que
                                // no reventara, y como al otro lado se tragan
                                // los errores, un fallo a mitad vaciaba la cola
                                // y se perdia lo que hubiera despues.
                                "(function(){if(!window.MiParteCola)return 'no';"
                                        + "try{return window.MiParteCola(" + cola + ")?'si':'no';}"
                                        + "catch(e){return 'no';}})()",
                                new ValueCallback<String>() {
                                    public void onReceiveValue(String r) {
                                        colaEnVuelo = null;
                                        if (r != null && r.contains("si")) {
                                            // Entregada, PERO NO GUARDADA. Aqui ya
                                            // no se vacia nada: la pagina llamara a
                                            // colaHecha() cuando el dato este
                                            // escrito en el disco. Si no llama, la
                                            // cola se queda y se reintenta al abrir
                                            // otra vez. Repetir es recuperable;
                                            // perder un apunte, no.
                                            colaEntregada = cola;
                                        } else {
                                            reintenta(intento);
                                        }
                                    }
                                });
                    }
                    if (hayAccion) {
                        web.requestFocus();
                        final String a = accion.replace("'", "");
                        web.evaluateJavascript(
                                "(function(){if(!window.MiParteAccion)return 'no';"
                                        + "try{window.MiParteAccion('" + a + "');return 'si';}"
                                        + "catch(e){return 'no';}})()",
                                new ValueCallback<String>() {
                                    public void onReceiveValue(String r) {
                                        if (r == null || !r.contains("si")) {
                                            accionPendiente = accion;   // no entro: se devuelve
                                            reintenta(intento);
                                        }
                                    }
                                });
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /** 40 intentos de 250 ms. Si en diez segundos la app no ha montado, la cola
     *  se queda para el proximo arranque en vez de perderse. */
    private void reintenta(final int intento) {
        if (intento >= 40 || web == null) return;
        web.postDelayed(new Runnable() {
            public void run() { sueltaPendientes(intento + 1); }
        }, 250);
    }
}
