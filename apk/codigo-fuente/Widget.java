package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * PARTE DE HOY. Obra, fecha y tareas que quedan, con el boton de camara.
 * El cuadro 4x2 lleva ademas PARTE DE HOY y APUNTAR TRABAJO.
 * Es uno de los dos que se ponen rojos cuando pasa la hora sin cerrar el parte.
 */
public class Widget extends AppWidgetProvider {

    int layout() { return R.layout.widget_grande; }
    boolean conBotones() { return true; }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, pinta(c));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i == null ? null : i.getAction();
        if (a != null && !AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) Widgets.refresca(c);
    }

    /** El puente del MainActivity sigue llamando aqui. */
    static void guardaEstado(Context c, String json) { WidgetDatos.guarda(c, json); }

    static void refresca(Context c) { Widgets.refresca(c); }

    RemoteViews pinta(Context c) {
        JSONObject o = WidgetDatos.estado(c);
        boolean deHoy = WidgetDatos.deHoy(c);
        boolean rojo = WidgetDatos.enRojo(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), layout());

        if (deHoy) {
            String obra = o.optString("obra", "");
            v.setTextViewText(R.id.wObra, obra.isEmpty() ? "SIN OBRA" : obra.toUpperCase());
            v.setTextViewText(R.id.wFecha, WidgetDatos.fechaLarga());
        } else {
            // Ningun parte hoy todavia: solo la fecha y la invitacion.
            v.setTextViewText(R.id.wObra, WidgetDatos.fechaLarga().toUpperCase());
            v.setTextViewText(R.id.wFecha, "toca para empezar");
        }
        // La cuenta de tareas se queda como estaba, sin avisar de que es vieja.
        int n = o.optJSONArray("tareas") == null ? 0 : o.optJSONArray("tareas").length();
        v.setTextViewText(R.id.wTareas, n == 0 ? "sin tareas pendientes"
                : n + (n == 1 ? " tarea pendiente" : " tareas pendientes"));

        v.setInt(R.id.wFondo, "setBackgroundResource",
                rojo ? R.drawable.widget_fondo_rojo : R.drawable.widget_fondo);
        int tinta = WidgetDatos.color(c, rojo ? R.color.w_rojo_texto : R.color.w_texto);
        int flojo = WidgetDatos.color(c, rojo ? R.color.w_rojo_flojo : R.color.w_flojo);
        v.setTextColor(R.id.wObra, tinta);
        v.setTextColor(R.id.wFecha, flojo);
        v.setTextColor(R.id.wTareas, flojo);

        v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "parte", 1));
        v.setOnClickPendingIntent(R.id.wTareas, Widgets.abre(c, "tareas", 2));
        v.setOnClickPendingIntent(R.id.wFoto, Widgets.camara(c, 9));
        if (conBotones()) {
            v.setOnClickPendingIntent(R.id.wParte, Widgets.abre(c, "parte", 3));
            v.setOnClickPendingIntent(R.id.wTrabajo, Widgets.abre(c, "trabajo", 4));
        }
        return v;
    }
}
