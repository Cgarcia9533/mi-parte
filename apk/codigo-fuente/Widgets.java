package com.miparte.montajes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Cosas que necesitan todos los proveedores: intents y repintado global. */
final class Widgets {

    static final String ACCION = "accion";
    static final String TACHA = "com.miparte.montajes.TACHA";

    private Widgets() { }

    static int banderas() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }

    /** Las PLANTILLAS de los widgets de lista tienen que ser mutables:
     *  setOnClickFillInIntent funde el intent de la fila dentro de la plantilla,
     *  y con FLAG_IMMUTABLE esa fusion no ocurre y el toque no hace nada. */
    static int banderasPlantilla() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) f |= PendingIntent.FLAG_MUTABLE;
        return f;
    }

    /** Abre la app haciendo algo: parte, tareas, obras, semana, trabajo, incidencia. */
    static PendingIntent abre(Context c, String accion, int codigo) {
        Intent i = new Intent(c, MainActivity.class);
        i.setAction("miparte." + accion);
        i.putExtra(ACCION, accion);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, codigo, i, banderas());
    }

    static PendingIntent camara(Context c, int codigo) {
        Intent i = new Intent(c, FotoRapida.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, codigo, i, banderas());
    }

    /** Escribir una tarea sin abrir la app: una ventanita encima de lo que
     *  tengas puesto, y la tarea se queda en la cola hasta que abras la app. */
    static PendingIntent tareaNueva(Context c, int codigo) {
        Intent i = new Intent(c, TareaRapida.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, codigo, i, banderas());
    }

    /** Un boton que no abre la app: manda un aviso al propio widget. */
    static PendingIntent aviso(Context c, Class<?> clase, String que, int codigo) {
        Intent i = new Intent(c, clase);
        i.setAction(TACHA);
        i.putExtra("que", que);
        return PendingIntent.getBroadcast(c, codigo, i, banderas());
    }

    private static final Class<?>[] TODOS = {
            Widget.class, WidgetFila.class, WidgetFichar.class, WidgetTareas.class,
            WidgetMaterial.class, WidgetVigilar.class, WidgetSemana.class, WidgetFoto.class,
            WidgetIncidencia.class, WidgetObra.class, WidgetResumen.class
    };

    /** Los tres que llevan lista con scroll: hay que avisarles aparte. */
    private static boolean conLista(Class<?> k) {
        return k == WidgetTareas.class || k == WidgetMaterial.class || k == WidgetVigilar.class;
    }

    /** Repinta todos los widgets puestos, de cualquier tipo. */
    static void refresca(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        for (Class<?> k : TODOS) {
            try {
                int[] ids = m.getAppWidgetIds(new ComponentName(c, k));
                if (ids == null || ids.length == 0) continue;
                Intent i = new Intent(c, k);
                i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                c.sendBroadcast(i);
                if (conLista(k)) m.notifyAppWidgetViewDataChanged(ids, R.id.wLista);
            } catch (Exception ignored) { }
        }
    }
}
