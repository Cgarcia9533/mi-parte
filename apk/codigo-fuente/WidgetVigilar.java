package com.miparte.montajes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * A VIGILAR. Los operarios que acumulan rojos en las ultimas cuatro semanas,
 * con el criterio que falla y cuantos rojos lleva. Solo se mira: al tocar,
 * abre la app en el equipo. Si no hay nadie, lo dice y ya esta.
 */
public class WidgetVigilar extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, pinta(c, id));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i == null ? null : i.getAction();
        if (a != null && !AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) Widgets.refresca(c);
    }

    static RemoteViews pinta(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lista);
        v.setTextViewText(R.id.wCab, "A VIGILAR \u00b7 4 SEMANAS");
        v.setTextViewText(R.id.wVacio, "Nadie acumula rojos. Todo en orden.");

        Intent datos = new Intent(c, ListaService.class);
        datos.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        datos.putExtra("tipo", "vigilar");
        datos.setData(Uri.parse(datos.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.wLista, datos);
        v.setEmptyView(R.id.wLista, R.id.wVacio);

        Intent tocar = new Intent(c, MainActivity.class);
        tocar.setAction("miparte.equipo");
        tocar.putExtra(Widgets.ACCION, "equipo");
        tocar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setPendingIntentTemplate(R.id.wLista,
                PendingIntent.getActivity(c, 41, tocar, Widgets.banderasPlantilla()));
        v.setOnClickPendingIntent(R.id.wCab, Widgets.abre(c, "equipo", 42));
        v.setViewVisibility(R.id.wMas, android.view.View.GONE);
        return v;
    }
}
