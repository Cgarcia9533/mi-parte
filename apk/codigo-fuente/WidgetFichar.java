package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * FICHAR. Dos botones grandes que sellan la hora exacta del movil en el parte
 * de hoy de la obra activa. La app esta cerrada, asi que el fichaje se queda en
 * cola y entra al abrirla, en el parte del dia en que se ficho.
 *
 * Si la entrada ya esta puesta, el boton se apaga hasta manana. La salida se
 * puede fichar sola: la entrada la pones tu despues en el parte.
 */
public class WidgetFichar extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, pinta(c));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        // Solo se repinta cuando el toque viene de nuestro boton. Sin este
        // filtro, refresca() manda un APPWIDGET_UPDATE que vuelve a entrar
        // aqui y se monta una tormenta de avisos que no para.
        if (i == null || !Widgets.TACHA.equals(i.getAction())) return;
        {
            String que = i.getStringExtra("que");
            if ("entrada".equals(que) && !WidgetDatos.yaFichado(c, "entrada")) WidgetDatos.ficha(c, "entrada");
            if ("salida".equals(que)) WidgetDatos.ficha(c, "salida");
        }
        Widgets.refresca(c);
    }

    static RemoteViews pinta(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_fichar);
        String ent = WidgetDatos.horaFichada(c, "entrada");
        String sal = WidgetDatos.horaFichada(c, "salida");
        boolean hayEnt = WidgetDatos.yaFichado(c, "entrada");

        v.setTextViewText(R.id.wPie,
                (ent.isEmpty() ? "sin entrada" : "entrada " + ent) + "  \u00b7  "
                        + (sal.isEmpty() ? "sin salida" : "salida " + sal));

        // El ambar va siempre en el boton que toca ahora: ENTRADA mientras no
        // hayas fichado, SALIDA en cuanto la entrada esta puesta. La entrada,
        // una vez puesta, se apaga: no se cambia hasta manana.
        v.setInt(R.id.wEntrada, "setBackgroundResource",
                hayEnt ? R.drawable.widget_boton_apagado : R.drawable.widget_boton);
        v.setTextColor(R.id.wEntrada,
                WidgetDatos.color(c, hayEnt ? R.color.w_apagado : R.color.w_sobre_ambar));
        if (!hayEnt) {
            v.setOnClickPendingIntent(R.id.wEntrada, Widgets.aviso(c, WidgetFichar.class, "entrada", 11));
        }

        // La salida, una vez fichada, se pone VERDE: se ve de un vistazo que el
        // dia esta cerrado. Pero sigue siendo un boton: si te has equivocado de
        // hora, vuelves a tocarlo y manda la nueva. Solo cambia el color.
        //   hueco   aun no hay entrada
        //   ambar   toca fichar salida
        //   verde   salida fichada  (se puede corregir tocando otra vez)
        boolean haySal = WidgetDatos.yaFichado(c, "salida");
        v.setInt(R.id.wSalida, "setBackgroundResource",
                haySal ? R.drawable.widget_boton_verde
                       : (hayEnt ? R.drawable.widget_boton : R.drawable.widget_boton_hueco));
        v.setTextColor(R.id.wSalida, WidgetDatos.color(c,
                haySal ? R.color.w_sobre_verde
                       : (hayEnt ? R.color.w_sobre_ambar : R.color.w_texto)));
        v.setOnClickPendingIntent(R.id.wSalida, Widgets.aviso(c, WidgetFichar.class, "salida", 12));
        v.setOnClickPendingIntent(R.id.wPie, Widgets.abre(c, "parte", 13));
        return v;
    }
}
