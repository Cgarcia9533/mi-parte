package com.miparte.montajes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Salta a la hora fijada. Avisa solo si el parte de hoy no constaba cerrado
 *  la ultima vez que la app estuvo abierta, y solo los dias marcados. */
public class AvisoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String accion = (i == null) ? null : i.getAction();

        // Prueba lanzada desde Ajustes: pasa por el mismo camino que el aviso de
        // verdad (alarma -> receptor -> notificacion) pero sin mirar el dia ni
        // si el parte esta cerrado. Si esta llega, el aviso de la hora llegara.
        if (Alarms.ACCION_PRUEBA.equals(accion)) {
            Avisos.muestra(c, "Mi Parte",
                    "Prueba del aviso: ha llegado con la app cerrada. A la hora que has puesto llegara igual.");
            return;
        }

        // Alarma de solo repintar: no avisa de nada, solo pone los widgets al
        // dia a la hora limite (el rojo) y se vuelve a programar.
        if (Alarms.ACCION_PINTA.equals(accion)) {
            Widgets.refresca(c);
            Alarms.repintaEnLaHora(c);
            return;
        }

        try {
            if (Prefs.avisoHoy(c)) {
                String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                if (!hoy.equals(Prefs.diaCerrado(c))) {
                    Avisos.muestra(c, "Mi Parte",
                            "El parte de hoy no esta cerrado. Terminalo antes de irte de la obra.");
                }
            }
        } finally {
            Alarms.reprograma(c);
            // Repintar los widgets AHORA. PARTE DE HOY y RESUMEN se ponen rojos
            // cuando pasa la hora sin cerrar el parte, pero eso lo deciden al
            // dibujarse, y su updatePeriodMillis es de media hora que ademas
            // Android aplaza mientras el movil duerme: el rojo podia tardar
            // horas o no llegar. Esta alarma SI es exacta, asi que es el momento
            // bueno para repintar.
            Widgets.refresca(c);
        }
    }
}
