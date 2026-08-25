package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * LA SEMANA. Lunes a domingo en columnas, con las dos cifras: la jornada
 * (de la entrada a la salida) arriba, y entre todos la cuadrilla debajo.
 */
public class WidgetSemana extends AppWidgetProvider {

    private static final int[] BARRA = { R.id.b0, R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6 };
    private static final int[] ROTULO = { R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6 };

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

    static RemoteViews pinta(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_semana);
        JSONObject o = WidgetDatos.estado(c);
        JSONArray sem = o.optJSONArray("semana");

        double tope = 1;
        for (int i = 0; sem != null && i < sem.length(); i++) {
            JSONObject d = sem.optJSONObject(i);
            if (d != null) tope = Math.max(tope, d.optDouble("jor", 0));
        }
        for (int i = 0; i < 7; i++) {
            JSONObject d = sem == null ? null : sem.optJSONObject(i);
            double h = d == null ? 0 : d.optDouble("jor", 0);
            boolean hoy = d != null && d.optBoolean("hoy", false);
            // La barra es un <clip> que se recorta por abajo con el nivel de la
            // imagen, de 0 a 10000. Con un View y padding no valdria: el padding
            // no recorta el fondo y saldrian las siete barras enteras.
            int nivel = h == 0 ? 0 : (int) Math.max(200, Math.round(h / tope * 10000));
            v.setImageViewResource(BARRA[i],
                    hoy ? R.drawable.widget_barra_hoy_clip : R.drawable.widget_barra_clip);
            v.setInt(BARRA[i], "setImageLevel", nivel);
            v.setTextViewText(ROTULO[i], d == null ? "" : d.optString("lab", ""));
            v.setTextColor(ROTULO[i], WidgetDatos.color(c, hoy ? R.color.w_ambar : R.color.w_flojo));
        }
        v.setTextViewText(R.id.wJor, WidgetDatos.horas(o.optDouble("semanaJor", 0)));
        v.setTextViewText(R.id.wTodos, WidgetDatos.horas(o.optDouble("semanaTodos", 0)) + " entre todos");
        v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "semana", 51));
        return v;
    }
}
