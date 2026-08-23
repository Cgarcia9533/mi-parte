package com.miparte.montajes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * MATERIAL PENDIENTE. Lo que falta por recibir o colocar: primero el de la obra
 * activa y debajo el del resto, separados. Tocas una linea y la das por
 * recibida y colocada; desaparece en el momento y entra al abrir la app.
 */
public class WidgetMaterial extends AppWidgetProvider {

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
            String id = i.getStringExtra("id");
            if (id != null) WidgetDatos.tachaMaterial(c, id, i.getStringExtra("dia"), "col");
        }
        Widgets.refresca(c);
    }

    static RemoteViews pinta(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lista);
        v.setTextViewText(R.id.wCab, "MATERIAL PENDIENTE");
        v.setTextViewText(R.id.wVacio, "Nada pendiente de recibir ni de colocar.");

        Intent datos = new Intent(c, ListaService.class);
        datos.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        datos.putExtra("tipo", "material");
        datos.setData(Uri.parse(datos.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.wLista, datos);
        v.setEmptyView(R.id.wLista, R.id.wVacio);

        Intent tocar = new Intent(c, WidgetMaterial.class);
        tocar.setAction(Widgets.TACHA);
        v.setPendingIntentTemplate(R.id.wLista,
                PendingIntent.getBroadcast(c, 31, tocar, Widgets.banderasPlantilla()));
        v.setOnClickPendingIntent(R.id.wCab, Widgets.abre(c, "material", 32));
        return v;
    }
}
