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

    /** El parte de hoy no esta cerrado y ya ha pasado la hora. */
    static boolean enRojo(Context c) {
        JSONObject o = estado(c);
        return deHoy(c) && o.optBoolean("avisa", false) && !o.optBoolean("cerrado", false)
                && pasoLaHora(o.optString("hora", "19:00"));
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

    static void tachaTarea(Context c, String txt) {
        try {
            JSONObject k = cola(c);
            JSONArray a = k.optJSONArray("tareas");
            if (a == null) a = new JSONArray();
            a.put(txt);
            k.put("tareas", a);
            pon(c, k);
        } catch (Exception ignored) { }
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
    static boolean tareaEnCola(Context c, String txt) {
        JSONArray a = cola(c).optJSONArray("tareas");
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (txt.equals(a.optString(i))) return true;
        return false;
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
    static String recoge(Context c) {
        JSONObject k = cola(c);
        if (k.length() == 0) return null;
        prefs(c).edit().remove(COLA).apply();
        return k.toString();
    }
}
