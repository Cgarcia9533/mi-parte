package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * LA LIBRETA en la pantalla de inicio del movil. Solo texto: se lee tal como
 * quedo en la app, una linea por fila para que haga scroll si es larga.
 *
 * El + abre una ventanita y apunta una linea al final sin abrir la app; se
 * queda en la cola y entra sola la proxima vez que la abras.
 */
public class WidgetNota extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, pinta(c, id));
    }

    static RemoteViews pinta(Context c, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_nota);
        // Si hay una edicion hecha aqui y sin volcar, se dice. Ese aviso es la
        // prueba de que la cola funciona: al abrir la app tiene que irse.
        boolean espera = WidgetDatos.hayLibretaPendiente(c);
        v.setTextViewText(R.id.wCab, espera ? "NOTAS · SIN VOLCAR" : "NOTAS");
        v.setTextViewText(R.id.wVacio,
                "La hoja está en blanco.\nToca + y apunta lo que quieras:\nideas, medidas, recados…");
        // «EDITAR» y no «+ NOTA»: la libreta no es una lista, se edita entera.
        v.setTextViewText(R.id.wMas, "EDITAR");

        Intent datos = new Intent(c, ListaService.class);
        datos.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        datos.putExtra("tipo", "nota");
        datos.setData(Uri.parse(datos.toUri(Intent.URI_INTENT_SCHEME)));
        v.setRemoteAdapter(R.id.wLista, datos);
        v.setEmptyView(R.id.wLista, R.id.wVacio);

        // Tocar una linea abre la app en la libreta: aqui no se edita.
        v.setPendingIntentTemplate(R.id.wLista, Widgets.abre(c, "notas", 26000 + id));
        v.setOnClickPendingIntent(R.id.wCab, Widgets.abre(c, "notas", 27000 + id));
        v.setViewVisibility(R.id.wMas, android.view.View.VISIBLE);
        v.setOnClickPendingIntent(R.id.wMas, Widgets.notaEdita(c, 28000 + id));
        return v;
    }
}
