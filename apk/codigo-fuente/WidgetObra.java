package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * OBRA ACTIVA. El nombre en grande y el cliente debajo. Al tocarlo abre la
 * lista de obras y eliges: con la app cerrada no se puede sacar una lista
 * encima de la pantalla de inicio.
 */
public class WidgetObra extends AppWidgetProvider {

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
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_obra);
        JSONObject o = WidgetDatos.estado(c);
        String obra = o.optString("obra", "");
        String cli = o.optString("cliente", "");
        v.setTextViewText(R.id.wObra, obra.isEmpty() ? "SIN OBRA" : obra.toUpperCase());
        v.setTextViewText(R.id.wFecha, cli.isEmpty() ? "toca para elegir obra" : cli);
        v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "obras", 81));
        return v;
    }
}
