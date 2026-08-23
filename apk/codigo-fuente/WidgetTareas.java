package com.miparte.montajes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * TAREAS PENDIENTES, con scroll. Tocas una linea y se tacha: desaparece de la
 * lista en el momento y la app se pone al dia cuando la abras.
 */
public class WidgetTareas extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, pinta(c, id));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        // Solo se repinta cuando el toque viene de nuestro boton. Sin este
        // filtro, refresca() manda un APPWIDGET_UPDATE que vuelve a entrar
        // aqui y se monta una tormenta de avisos que no para.
        if (i == null || !Widgets.TACHA.equals(i.getAction())) return;
        {
            String txt = i.getStringExtra("txt");
            if (txt != null) WidgetDatos.tachaTarea(c, txt);
        }
        Widgets.refresca(c);
    }

    static RemoteViews pinta(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lista);
        v.setTextViewText(R.id.wCab, "PENDIENTES");
        v.setTextViewText(R.id.wVacio, "Nada pendiente.");

        Intent datos = new Intent(c, ListaService.class);
        datos.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        datos.putExtra("tipo", "tareas");
        datos.setData(Uri.parse(datos.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.wLista, datos);
        v.setEmptyView(R.id.wLista, R.id.wVacio);

        Intent tocar = new Intent(c, WidgetTareas.class);
        tocar.setAction(Widgets.TACHA);
        v.setPendingIntentTemplate(R.id.wLista,
                PendingIntent.getBroadcast(c, 21, tocar, Widgets.banderasPlantilla()));
        v.setOnClickPendingIntent(R.id.wCab, Widgets.abre(c, "tareas", 22));
        return v;
    }
}
