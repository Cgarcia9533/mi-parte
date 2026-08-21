package com.miparte.montajes;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Estado que necesita la alarma nativa cuando la app esta cerrada. */
public final class Prefs {
    private static final String FILE = "miparte-nativo";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean avisoActivo(Context c) { return get(c).getBoolean("notif", false); }

    /** La app manda "19:00", "07:30,19:00" o "07:30,19:00|1111100"
     *  (las horas separadas por comas y los siete dias, lunes primero). */
    private static String crudo(Context c) { return get(c).getString("hora", "19:00"); }

    /** Todas las horas de aviso, ordenadas como las manda la app. */
    public static String[] horas(Context c) {
        String s = crudo(c);
        int i = s.indexOf('|');
        String[] partes = (i < 0 ? s : s.substring(0, i)).split(",");
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String p : partes) { String t = p.trim(); if (!t.isEmpty()) out.add(t); }
        if (out.isEmpty()) out.add("19:00");
        return out.toArray(new String[0]);
    }

    /** La primera, para lo que solo espere una. */
    public static String hora(Context c) { return horas(c)[0]; }

    /** Mascara de siete caracteres, lunes primero. Sin mascara: todos los dias. */
    public static String dias(Context c) {
        String s = crudo(c);
        int i = s.indexOf('|');
        if (i < 0 || s.length() < i + 8) return "1111111";
        String m = s.substring(i + 1, i + 8);
        if (m.length() != 7) return "1111111";
        for (int k = 0; k < 7; k++) {
            char ch = m.charAt(k);
            if (ch != '0' && ch != '1') return "1111111";
        }
        return m;
    }

    /** Activo Y hoy es uno de los dias marcados. */
    public static boolean avisoHoy(Context c) {
        if (!avisoActivo(c)) return false;
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // 1 = domingo
        int idx = (dow + 5) % 7;                                    // 0 = lunes
        return dias(c).charAt(idx) == '1';
    }

    /** Ultimo dia (yyyy-MM-dd) en el que el parte constaba cerrado. */
    public static String diaCerrado(Context c) { return get(c).getString("cerrado", ""); }

    /** Momento del proximo aviso programado, o 0. Solo para enseñarlo en Ajustes. */
    public static long proxima(Context c) { return get(c).getLong("proxima", 0L); }

    public static void guardaProxima(Context c, long t) {
        get(c).edit().putLong("proxima", t).apply();
    }

    public static void guarda(Context c, boolean notif, String hora, String cerrado) {
        get(c).edit()
              .putBoolean("notif", notif)
              .putString("hora", hora == null ? "19:00" : hora)
              .putString("cerrado", cerrado == null ? "" : cerrado)
              .apply();
    }
}
