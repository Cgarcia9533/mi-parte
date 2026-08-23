package com.miparte.montajes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Vuelve a poner las alarmas cuando se pierden solas: al arrancar el movil, al
 *  instalar encima, al cambiar la hora o la zona horaria, y (1.12) cuando el
 *  usuario concede el permiso de alarmas exactas. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Alarms.reprograma(c);
        // Al arrancar el movil, al cambiar la hora y al instalar encima, los
        // widgets pueden estar enseñando el rojo (o la falta de rojo) de antes.
        Widgets.refresca(c);
    }
}
