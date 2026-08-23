package com.miparte.montajes;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Lo que todos los widgets comparten: el estado que manda la app web y la cola
 * de lo que se toca con la app cerrada.
 *
 * El widget no puede leer el localStorage del WebView. La app web llama a
 * MiParteNative.syncWidget cada vez que guarda y aqui se queda el JSON entero.
 * Los dias que no abres la app, el dato es del ultimo dia que la abriste.
 */
final class WidgetDatos {

    static final String PREFS = "miparte";
    static final String JSON = "wJson";
    static final String COLA = "wCola";

    private WidgetDatos() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void guarda(Context c, String json) {
        if (json == null) return;
        try {
            new JSONObject(json);                 // que sea JSON valido
            prefs(c).edit().putString(JSON, json).apply();
        } catch (Exception ignored) { }
    }

    static JSONObject estado(Context c) {
        try {
            return new JSONObject(prefs(c).getString(JSON, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** true si lo que hay guardado es de hoy. */
    static boolean deHoy(Context c) {
        return hoyKey().equals(estado(c).optString("dia", ""));
    }

    static int color(Context c, int id) {
        if (Build.VERSION.SDK_INT >= 23) return c.getColor(id);
        return c.getResources().getColor(id);
    }

    static String hoyKey() {
        Calendar k = Calendar.getInstance();
        return String.format("%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1,
                k.get(Calendar.DAY_OF_MONTH));
    }

    static String horaAhora() {
        Calendar k = Calendar.getInstance();
        return String.format("%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE));
    }

    static String fechaLarga() {
        Calendar k = Calendar.getInstance();
        String[] d = { "domingo", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado" };
        String[] m = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
                "septiembre", "octubre", "noviembre", "diciembre" };
        return d[k.get(Calendar.DAY_OF_WEEK) - 1] + " " + k.get(Calendar.DAY_OF_MONTH)
                + " de " + m[k.get(Calendar.MONTH)];
    }

    static String horas(double h) {
        int t = (int) Math.round(h * 60);
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }

    /** Ha pasado ya la hora del aviso. */
    static boolean pasoLaHora(String hhmm) {
        try {
            String[] t = hhmm.split(":");
            int lim = Integer.parseInt(t[0]) * 60 + Integer.parseInt(t[1]);
            Calendar k = Calendar.getInstance();
            return k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE) >= lim;
        } catch (Exception e) {
            return false;
        }
    }

    /** La hora a la que la app da por tarde el parte. */
    static String horaLimite(Context c) {
        String h = estado(c).optString("hora", "");
        return h.isEmpty() ? "19:00" : h;
    }

    /** El parte de hoy no esta cerrado y ya ha pasado la hora.
     *
     *  "avisa" es el interruptor de notificaciones de la app: el rojo solo
     *  sale si lo tienes encendido. Es a proposito, no es un descuido: el rojo
     *  del widget es la version silenciosa del aviso, y si has apagado los
     *  avisos es que no quieres que te den la tabarra por ningun lado.
     *
     *  Ojo: la banda ambar de dentro de la app NO mira este interruptor, asi
     *  que con los avisos apagados la app te avisa y el widget se queda negro.
     *  Es lo que hay: son dos cosas distintas a proposito. */
    static boolean enRojo(Context c) {
        JSONObject o = estado(c);
        return deHoy(c) && o.optBoolean("avisa", false) && !o.optBoolean("cerrado", false)
                && pasoLaHora(horaLimite(c));
    }

    // ---------------- la cola ----------------

    static JSONObject cola(Context c) {
        try {
            return new JSONObject(prefs(c).getString(COLA, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void pon(Context c, JSONObject cola) {
        prefs(c).edit().putString(COLA, cola.toString()).apply();
    }

    static void ficha(Context c, String tipo) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray("fichajes");
            if (a == null) a = new JSONArray();
            JSONObject f = new JSONObject();
            f.put("dia", hoyKey());          // el fichaje va al dia en que se ficho
            f.put("tipo", tipo);
            f.put("hora", horaAhora());      // la hora exacta del movil
            a.put(f);
            k.put("fichajes", a);
            pon(c, k);
        } catch (Exception ignored) { }
    }

    /** true si ya hay entrada, sea del parte o de la cola: el boton se apaga. */
    static boolean yaFichado(Context c, String tipo) {
        JSONObject o = estado(c);
        if (deHoy(c) && o.optString(tipo, "").length() > 0) return true;
        JSONArray a = cola(c).optJSONArray("fichajes");
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject f = a.optJSONObject(i);
            if (f != null && hoyKey().equals(f.optString("dia")) && tipo.equals(f.optString("tipo"))) return true;
        }
        return false;
    }

    static String horaFichada(Context c, String tipo) {
        JSONArray a = cola(c).optJSONArray("fichajes");
        if (a != null) {
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject f = a.optJSONObject(i);
                if (f != null && hoyKey().equals(f.optString("dia")) && tipo.equals(f.optString("tipo"))) {
                    return f.optString("hora", "");
                }
            }
        }
        return deHoy(c) ? estado(c).optString(tipo, "") : "";
    }

    // --- listas de la cola de tareas ---
    //   tareas     : marcar como hecha lo que en la app esta sin hacer
    //   destareas  : quitar la marca a lo que en la app esta hecho
    //   nuevas     : tareas escritas desde el widget, aun sin entrar en la app

    static boolean enLista(Context c, String lista, String txt) {
        JSONArray a = cola(c).optJSONArray(lista);
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (txt.equals(a.optString(i))) return true;
        return false;
    }

    private static void meteEnLista(Context c, String lista, String txt) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray(lista);
            if (a == null) a = new JSONArray();
            a.put(txt);
            k.put(lista, a);
            pon(c, k);
        } catch (Exception ignored) { }
    }

    private static void sacaDeLista(Context c, String lista, String txt) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray(lista);
            if (a == null) return;
            JSONArray out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                String x = a.optString(i, "");
                if (!txt.equals(x)) out.put(x);
            }
            if (out.length() == 0) k.remove(lista); else k.put(lista, out);
            pon(c, k);
        } catch (Exception ignored) { }
    }

    /** Como esta la tarea en la app, sin contar la cola. */
    static boolean hechaEnApp(Context c, String txt) {
        JSONArray a = estado(c).optJSONArray("tareas");
        for (int i = 0; a != null && i < a.length(); i++) {
            Object o = a.opt(i);
            if (txt.equals(tareaTxt(o))) return tareaHecha(o);
        }
        return false;
    }

    static boolean estaEnApp(Context c, String txt) {
        JSONArray a = estado(c).optJSONArray("tareas");
        for (int i = 0; a != null && i < a.length(); i++) {
            if (txt.equals(tareaTxt(a.opt(i)))) return true;
        }
        return false;
    }

    /** Como tiene que verse la tarea AHORA: lo de la app, mas lo que espera
     *  en la cola. Es lo unico que miran la lista y los contadores. */
    static boolean tareaVista(Context c, String txt, boolean hechaApp) {
        if (enLista(c, "destareas", txt)) return false;
        if (enLista(c, "tareas", txt)) return true;
        return hechaApp;
    }

    /** Igual, cuando no se tiene a mano como esta en la app. Cuesta releer el
     *  estado entero, asi que quien recorra la lista usa la de arriba. */
    static boolean tareaVista(Context c, String txt) {
        return tareaVista(c, txt, hechaEnApp(c, txt));
    }

    /** Un toque le da la vuelta a la tarea, este como este en la app.
     *
     *  1.3: antes solo sabia tachar. Si la tarea ya venia hecha de la app, el
     *  toque encolaba "tachar" otra vez y la app, que busca la tarea sin
     *  hacer, no encontraba nada: por eso no habia manera de destacharla
     *  desde el widget. Ahora se encola en una lista o en la otra segun como
     *  este, y un segundo toque retira lo encolado y la deja como estaba.
     *
     *  Las tareas escritas en el widget y aun sin volcar se borran de un
     *  toque: no estan en la app, tacharlas no significa nada. */
    static void tachaTarea(Context c, String txt) {
        if (txt == null || txt.isEmpty()) return;
        if (enLista(c, "nuevas", txt) && !estaEnApp(c, txt)) { sacaDeLista(c, "nuevas", txt); return; }
        if (enLista(c, "tareas", txt))    { sacaDeLista(c, "tareas", txt);    return; }
        if (enLista(c, "destareas", txt)) { sacaDeLista(c, "destareas", txt); return; }
        meteEnLista(c, hechaEnApp(c, txt) ? "destareas" : "tareas", txt);
    }

    /** Tarea escrita desde el widget. Entra en la app al abrirla. */
    static void nuevaTarea(Context c, String txt) {
        String s = txt == null ? "" : txt.trim();
        if (s.isEmpty()) return;
        if (estaEnApp(c, s) || enLista(c, "nuevas", s)) return;
        meteEnLista(c, "nuevas", s);
    }

    /** Las escritas en el widget que aun no han entrado en la app. */
    static JSONArray nuevasEnCola(Context c) {
        JSONArray a = cola(c).optJSONArray("nuevas");
        return a == null ? new JSONArray() : a;
    }

    static void tachaMaterial(Context c, String id, String dia, String campo) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray("material");
            if (a == null) a = new JSONArray();
            JSONObject m = new JSONObject();
            m.put("id", id); m.put("dia", dia); m.put("campo", campo);
            a.put(m);
            k.put("material", a);
            pon(c, k);
        } catch (Exception ignored) { }
    }

    static void ponFoto(Context c, String b64, String hora, String dia) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray("fotos");
            if (a == null) a = new JSONArray();
            JSONObject f = new JSONObject();
            f.put("b64", b64); f.put("hora", hora); f.put("dia", dia);
            a.put(f);
            k.put("fotos", a);
            pon(c, k);
        } catch (Exception ignored) { }
    }

    /** true si esa tarea esta ya tachada en la cola: desaparece de la lista. */
    /** Una tarea puede llegar como texto suelto (formato viejo) o como objeto
     *  {txt, hecha}. Se aceptan los dos. */
    static String tareaTxt(Object o) {
        if (o instanceof JSONObject) return ((JSONObject) o).optString("txt", "");
        return o == null ? "" : String.valueOf(o);
    }

    static boolean tareaHecha(Object o) {
        return (o instanceof JSONObject) && ((JSONObject) o).optBoolean("hecha", false);
    }

    static int tareasPendientes(Context c) {
        JSONArray a = estado(c).optJSONArray("tareas");
        if (a == null) return 0;
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            Object o = a.opt(i);
            String t = tareaTxt(o);
            if (t.isEmpty() || tareaVista(c, t, tareaHecha(o))) continue;
            n++;
        }
        // las escritas desde el widget cuentan desde el momento en que se escriben
        JSONArray nv = nuevasEnCola(c);
        for (int i = 0; i < nv.length(); i++) {
            String t = nv.optString(i, "");
            if (!t.isEmpty() && !estaEnApp(c, t)) n++;
        }
        return n;
    }

    static int materialPendiente(Context c) {
        JSONArray a = estado(c).optJSONArray("material");
        if (a == null) return 0;
        int n = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m == null) continue;
            String id = m.optString("id", "");
            if (id.isEmpty() || materialEnCola(c, id)) continue;
            n++;
        }
        return n;
    }

    /** Cuantas cosas hay esperando a que se abra la app. Se enseña en la
     *  cabecera del widget de pendientes: si sube y no baja al abrir la app,
     *  es que la cola no esta llegando. */
    static int enEspera(Context c) {
        JSONObject k = cola(c);
        int n = 0;
        for (String cl : new String[]{"tareas", "destareas", "nuevas", "material", "fichajes", "fotos"}) {
            JSONArray a = k.optJSONArray(cl);
            if (a != null) n += a.length();
        }
        return n;
    }

    static boolean materialEnCola(Context c, String id) {
        JSONArray a = cola(c).optJSONArray("material");
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m != null && id.equals(m.optString("id"))) return true;
        }
        return false;
    }

    /** Vacia la cola y la devuelve para la app web. null si no habia nada. */
    /** Devuelve la cola SIN vaciarla. Se vacia aparte, y solo cuando la app
     *  confirma que se ha quedado con ella. */
    static String recoge(Context c) {
        JSONObject k = cola(c);
        if (k.length() == 0) return null;
        return k.toString();
    }

    static void vaciaCola(Context c) {
        prefs(c).edit().remove(COLA).apply();
    }
}
