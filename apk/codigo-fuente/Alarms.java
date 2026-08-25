package com.miparte.montajes;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Programa los avisos diarios: uno por cada hora fijada en la app (hasta MAX).
 *
 *  1.12: la alarma es EXACTA cuando el sistema lo permite.
 *  Hasta la 1.11 era inexacta (setAndAllowWhileIdle a secas) y Android la
 *  metia en el mismo saco que las alarmas de fondo: en cuanto el movil ponia
 *  la app en un cubo de reposo (Samsung: «poner la app en suspension»), la
 *  alarma se aplazaba horas o directamente no llegaba. Por eso el aviso de la
 *  activacion se veia (ese lo lanza la app en ese momento) y el de las 19:00 no.
 *
 *  1.12: ademas la fecha se calcula saltando los dias NO marcados, en vez de
 *  despertar todos los dias y callarse. Asi los dias se respetan aunque el
 *  receptor no llegue a ejecutarse. */
public final class Alarms {

    private static final int MAX = 6;
    private static final int BASE = 7301;
    private static final int PRUEBA = 7399;
    private static final int PINTA = 7350;

    static final String ACCION_PRUEBA = "miparte.prueba";
    static final String ACCION_PINTA = "miparte.repinta";

    private static int flags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }

    private static PendingIntent pi(Context c, int i) {
        Intent in = new Intent(c, AvisoReceiver.class);
        in.setAction("miparte.aviso." + i);
        return PendingIntent.getBroadcast(c, BASE + i, in, flags());
    }

    /** ¿Nos deja el sistema poner alarmas exactas? Antes de Android 12 siempre. */
    public static boolean exactas(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Cuando toca el proximo aviso de esa hora, saltando los dias sin marcar. */
    static long proxima(Context c, String hhmm) {
        int h = 19, m = 0;
        try {
            String[] hm = hhmm.split(":");
            h = Integer.parseInt(hm[0].trim());
            if (hm.length > 1) m = Integer.parseInt(hm[1].trim());
        } catch (Exception ignored) { }

        String dias = Prefs.dias(c);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // Medio minuto de margen: si la alarma salta un pelo antes de la hora,
        // que no se reprograme para dentro de un minuto y avise dos veces.
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 30000L) cal.add(Calendar.DAY_OF_YEAR, 1);

        for (int i = 0; i < 8; i++) {
            int idx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // 0 = lunes
            if (dias.charAt(idx) == '1') return cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();   // ningun dia marcado: no llega aqui con mascara valida
    }

    /** Hoy a esa hora; si ya paso, mañana. Sin mirar los dias marcados. */
    private static long enPunto(String hhmm) {
        int h = 19, m = 0;
        try {
            String[] hm = hhmm.split(":");
            h = Integer.parseInt(hm[0].trim());
            if (hm.length > 1) m = Integer.parseInt(hm[1].trim());
        } catch (Exception ignored) { }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 5);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    /** Alarma que SOLO repinta los widgets al llegar la hora limite.
     *
     *  Va aparte de los avisos a proposito. La de los avisos solo se pone si
     *  tienes las notificaciones encendidas y solo los dias marcados; el rojo
     *  del widget no depende de ninguna de las dos cosas, igual que la banda
     *  de la app. Sin esta alarma, con los avisos apagados el rojo tenia que
     *  esperar a que abrieras la app o a la actualizacion de media hora, que
     *  Android aplaza mientras el movil duerme. */
    public static void repintaEnLaHora(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent in = new Intent(c, AvisoReceiver.class);
        in.setAction(ACCION_PINTA);
        PendingIntent p = PendingIntent.getBroadcast(c, PINTA, in, flags());
        pon(am, enPunto(WidgetDatos.horaLimite(c)), p, exactas(c));
    }

    public static void reprograma(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        String[] horas = Prefs.horas(c);
        boolean on = Prefs.avisoActivo(c) && Prefs.dias(c).indexOf('1') >= 0;
        boolean ex = exactas(c);
        long prox = 0;

        for (int i = 0; i < MAX; i++) {
            PendingIntent p = pi(c, i);
            if (!on || i >= horas.length) { am.cancel(p); continue; }
            long t = proxima(c, horas[i]);
            pon(am, t, p, ex);
            if (prox == 0 || t < prox) prox = t;
        }
        Prefs.guardaProxima(c, on ? prox : 0);
        repintaEnLaHora(c);
    }

    /** Exacta si se puede; si no, la de siempre. Nunca revienta por esto. */
    private static void pon(AlarmManager am, long t, PendingIntent p, boolean exactas) {
        if (exactas) {
            try {
                if (Build.VERSION.SDK_INT >= 23) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p); return; }
                am.setExact(AlarmManager.RTC_WAKEUP, t, p);
                return;
            } catch (Throwable ignored) { }
        }
        try {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            else am.set(AlarmManager.RTC_WAKEUP, t, p);
        } catch (Throwable ignored) { }
    }

    /** Aviso de prueba: salta una sola vez dentro de N segundos, mire lo que mire
     *  el receptor. Sirve para ver si el movil deja pasar la alarma cuando la
     *  app esta cerrada, que es justo lo que fallaba. */
    public static void prueba(Context c, int segundos) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent in = new Intent(c, AvisoReceiver.class);
        in.setAction(ACCION_PRUEBA);
        PendingIntent p = PendingIntent.getBroadcast(c, PRUEBA, in, flags());
        if (segundos < 5) segundos = 5;
        pon(am, System.currentTimeMillis() + segundos * 1000L, p, exactas(c));
    }
}
