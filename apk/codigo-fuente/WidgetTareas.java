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
            String lid = i.getStringExtra("lista");
            if (lid == null || lid.isEmpty()) lid = "tareas";
            if (txt != null) WidgetDatos.tachaTarea(c, lid, txt);
        }
        Widgets.refresca(c);
    }

    /** Al quitar el widget de la pantalla se olvida que lista tenia. */
    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetDatos.olvidaLista(c, id);
        super.onDeleted(c, ids);
    }

    static RemoteViews pinta(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_lista);
        String lid = WidgetDatos.listaDe(c, id);
        String nom = WidgetDatos.nomLista(c, lid).toUpperCase();
        int espera = WidgetDatos.enEspera(c);
        // Si hay algo esperando a que se abra la app, se dice. Sirve de aviso y
        // tambien para saber si la cola esta llegando: al abrir la app deberia
        // desaparecer.
        v.setTextViewText(R.id.wCab, espera == 0 ? nom : nom + " · " + espera + " SIN VOLCAR");
        v.setTextViewText(R.id.wVacio, "Lista vacía.");

        Intent datos = new Intent(c, ListaService.class);
        datos.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        datos.putExtra("tipo", "tareas");
        datos.putExtra("lista", lid);
        datos.setData(Uri.parse(datos.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.wLista, datos);
        v.setEmptyView(R.id.wLista, R.id.wVacio);

        // Los codigos van por widget: si fueran fijos, dos widgets de listas
        // distintas compartirian el mismo intent y el + apuntaria en la que no es.
        Intent tocar = new Intent(c, WidgetTareas.class);
        tocar.setAction(Widgets.TACHA);
        tocar.putExtra("lista", lid);
        v.setPendingIntentTemplate(R.id.wLista,
                PendingIntent.getBroadcast(c, 21000 + id, tocar, Widgets.banderasPlantilla()));
        v.setOnClickPendingIntent(R.id.wCab, Widgets.abre(c, "tareas", 22000 + id));
        v.setViewVisibility(R.id.wMas, android.view.View.VISIBLE);
        v.setOnClickPendingIntent(R.id.wMas, Widgets.tareaNueva(c, 23000 + id, lid));
        return v;
    }
}
