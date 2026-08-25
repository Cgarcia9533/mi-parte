package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/**
 * INCIDENCIA. Un icono 1x1: abre el paso 5 con una linea nueva vacia y el tipo
 * lo eliges dentro.
 */
public class WidgetIncidencia extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_icono);
            v.setImageViewResource(R.id.wIcono, R.drawable.ic_aviso);
            v.setInt(R.id.wFondo, "setBackgroundResource", R.drawable.widget_icono_rojo);
            v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "incidencia", 71));
            m.updateAppWidget(id, v);
        }
    }
}
