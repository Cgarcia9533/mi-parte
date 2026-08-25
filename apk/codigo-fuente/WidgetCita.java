package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.util.Calendar;

/**
 * PROXIMA CITA. Lo primero que viene por delante en la agenda: cuando es, de
 * que va, la obra y el sitio, con la raya del color de la obra.
 *
 * Al tocarlo se abre la agenda EN ESE DIA (accion "agenda:2026-08-26"). Si no
 * hay nada apuntado, sale el boton de apuntar cita, que abre la ficha en
 * blanco del dia de hoy.
 *
 * El texto de "hoy" y "manana" viene dentro del JSON, no escrito aqui: asi no
 * hay acentos en el codigo y, si pasan los dias sin abrir la app, el widget
 * recalcula la palabra comparando la fecha guardada con la de verdad.
 */
public class WidgetCita extends AppWidgetProvider {

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
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_cita);
        JSONObject o = WidgetDatos.estado(c).optJSONObject("cita");
        boolean hay = o != null && o.optString("tit", "").length() > 0;

        v.setViewVisibility(R.id.wCuerpo, hay ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.wVacio, hay ? View.GONE : View.VISIBLE);

        if (!hay) {
            v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "agenda", 91));
            v.setOnClickPendingIntent(R.id.wBoton, Widgets.abre(c, "citanueva", 92));
            return v;
        }

        String dia = o.optString("dia", WidgetDatos.hoyKey());
        String cuando;
        if (dia.equals(WidgetDatos.hoyKey())) cuando = o.optString("txtHoy", "");
        else if (dia.equals(manana())) cuando = o.optString("txtManana", "");
        else cuando = o.optString("fecha", "");
        String hora = o.optString("hora", "");
        v.setTextViewText(R.id.wCuando, hora.isEmpty() ? cuando : cuando + "   " + hora);
        v.setTextViewText(R.id.wTit, o.optString("tit", ""));

        String sub = o.optString("sub", "");
        v.setTextViewText(R.id.wSub, sub);
        v.setViewVisibility(R.id.wSub, sub.isEmpty() ? View.GONE : View.VISIBLE);

        int col = color(o.optString("col", ""));
        if (col != 0) v.setInt(R.id.wColor, "setBackgroundColor", col);

        v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "agenda:" + dia, 93));
        return v;
    }

    /** La clave del dia de manana, para saber si la cita es de manana. */
    private static String manana() {
        Calendar k = Calendar.getInstance();
        k.add(Calendar.DAY_OF_MONTH, 1);
        return String.format("%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1,
                k.get(Calendar.DAY_OF_MONTH));
    }

    /** El color que manda la app: el de la obra, o el que le pusiste a la cita. */
    private static int color(String hex) {
        try {
            String h = hex == null ? "" : hex.trim();
            if (h.isEmpty()) return 0;
            return Color.parseColor(h.charAt(0) == '#' ? h : "#" + h);
        } catch (Exception e) {
            return 0;
        }
    }
}
