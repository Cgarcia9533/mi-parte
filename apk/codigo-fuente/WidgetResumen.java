package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

/**
 * RESUMEN. El grande, y solo se mira: ningun boton dentro. Obra y fecha arriba,
 * y debajo seis cifras: pasos del parte, operarios, horas de hoy, horas de la
 * semana, tareas pendientes y material pendiente. Al tocarlo, abre la app.
 *
 * Es el otro que se pone rojo cuando pasa la hora sin cerrar el parte.
 */
public class WidgetResumen extends AppWidgetProvider {

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
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_resumen);
        JSONObject o = WidgetDatos.estado(c);
        boolean deHoy = WidgetDatos.deHoy(c);
        boolean rojo = WidgetDatos.enRojo(c);

        String obra = o.optString("obra", "");
        v.setTextViewText(R.id.wObra, !deHoy ? WidgetDatos.fechaLarga().toUpperCase()
                : (obra.isEmpty() ? "SIN OBRA" : obra.toUpperCase()));
        v.setTextViewText(R.id.wFecha, deHoy ? WidgetDatos.fechaLarga() : "toca para empezar");

        int tareas = WidgetDatos.tareasPendientes(c);
        int material = WidgetDatos.materialPendiente(c);

        cifra(c, v, R.id.c1, R.id.r1, deHoy ? o.optInt("pasos", 0) + " de 6" : "\u2014", "PASOS DEL PARTE", rojo);
        cifra(c, v, R.id.c2, R.id.r2, deHoy ? String.valueOf(o.optInt("ops", 0)) : "\u2014", "OPERARIOS HOY", rojo);
        cifra(c, v, R.id.c3, R.id.r3, deHoy && !o.optString("entrada", "").isEmpty()
                ? o.optString("entrada") + " a " + (o.optString("salida", "").isEmpty() ? "?" : o.optString("salida"))
                : "sin fichar", "HORARIO DE HOY", rojo);
        cifra(c, v, R.id.c4, R.id.r4, WidgetDatos.horas(o.optDouble("semanaJor", 0)) + " h", "ESTA SEMANA", rojo);
        cifra(c, v, R.id.c5, R.id.r5, String.valueOf(tareas), "TAREAS PENDIENTES", rojo);
        cifra(c, v, R.id.c6, R.id.r6, String.valueOf(material), "MATERIAL PENDIENTE", rojo);

        v.setInt(R.id.wFondo, "setBackgroundResource",
                rojo ? R.drawable.widget_fondo_rojo : R.drawable.widget_fondo);
        v.setTextColor(R.id.wObra, WidgetDatos.color(c, rojo ? R.color.w_rojo_texto : R.color.w_texto));
        v.setTextColor(R.id.wFecha, WidgetDatos.color(c, rojo ? R.color.w_rojo_flojo : R.color.w_flojo));
        v.setOnClickPendingIntent(R.id.wFondo, Widgets.abre(c, "parte", 91));
        return v;
    }

    private static void cifra(Context c, RemoteViews v, int idCifra, int idRot, String cifra, String rotulo, boolean rojo) {
        v.setTextViewText(idCifra, cifra);
        v.setTextViewText(idRot, rotulo);
        v.setTextColor(idCifra, WidgetDatos.color(c, rojo ? R.color.w_rojo_texto : R.color.w_texto));
        v.setTextColor(idRot, WidgetDatos.color(c, rojo ? R.color.w_rojo_flojo : R.color.w_flojo));
    }
}
