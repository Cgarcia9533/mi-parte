package com.miparte.montajes;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class Avisos {
    /** 1.12: canal nuevo. El de la 1.11 («miparte-cierre») se creaba una sola
     *  vez y despues Android ya no admite cambios; si en algun arranque quedo
     *  con importancia baja o el usuario lo silencio sin darse cuenta, el aviso
     *  ya no volvia a sonar y no habia manera de arreglarlo desde la app.
     *  Con un id nuevo el canal se crea limpio y en alto. */
    public static final String CANAL = "miparte-cierre-2";
    private static final String CANAL_VIEJO = "miparte-cierre";

    public static void creaCanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try { nm.deleteNotificationChannel(CANAL_VIEJO); } catch (Exception ignored) { }
        if (nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel ch = new NotificationChannel(CANAL, "Cierre del parte", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Aviso a la hora fijada si el parte del dia no esta cerrado.");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 120, 60, 120});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setShowBadge(true);
        try { ch.setBypassDnd(false); } catch (Exception ignored) { }
        nm.createNotificationChannel(ch);
    }

    @SuppressWarnings("deprecation")
    public static void muestra(Context c, String titulo, String texto) {
        creaCanal(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent abrir = new Intent(c, MainActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 0, abrir, flags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(c, CANAL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif)
         .setContentTitle(titulo)
         .setContentText(texto)
         .setStyle(new Notification.BigTextStyle().bigText(texto))
         .setAutoCancel(true)
         .setWhen(System.currentTimeMillis())
         .setShowWhen(true)
         .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
            b.setCategory(Notification.CATEGORY_REMINDER);
        }
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH);
            b.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
            b.setVibrate(new long[]{0, 120, 60, 120});
        }
        try {
            nm.notify(4021, b.build());
        } catch (Exception ignored) { }
    }
}
