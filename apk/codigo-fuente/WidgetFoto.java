package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** FOTO RAPIDA. Un icono 1x1 y nada mas: dispara, y sigue disparando. */
public class WidgetFoto extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_icono);
            v.setImageViewResource(R.id.wIcono, R.drawable.ic_camara);
            v.setOnClickPendingIntent(R.id.wFondo, Widgets.camara(c, 61));
            m.updateAppWidget(id, v);
        }
    }
}
