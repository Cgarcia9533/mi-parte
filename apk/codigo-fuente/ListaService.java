package com.miparte.montajes;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * El proveedor de filas de los tres widgets de lista: tareas, material y a
 * vigilar. Lee el estado que dejo la app web y le resta lo que ya esta en la
 * cola, para que lo que tachas desaparezca en el momento.
 */
public class ListaService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent i) {
        String tipo = i.getStringExtra("tipo");
        return new Factory(getApplicationContext(), tipo == null ? "tareas" : tipo);
    }

    private static final class Factory implements RemoteViewsFactory {

        private final Context c;
        private final String tipo;
        private JSONArray filas = new JSONArray();

        Factory(Context c, String tipo) { this.c = c; this.tipo = tipo; }

        @Override public void onCreate() { }
        @Override public void onDestroy() { }
        @Override public int getCount() { return filas.length(); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean hasStableIds() { return false; }
        @Override public RemoteViews getLoadingView() { return null; }

        @Override
        public void onDataSetChanged() {
            JSONObject o = WidgetDatos.estado(c);
            JSONArray out = new JSONArray();
            try {
                if ("tareas".equals(tipo)) {
                    // Lo tachado ya no desaparece: se queda con la casilla verde
                    // y el texto tachado, igual que en la app. El estado que se
                    // pinta es el de la app mas lo que espera en la cola, para
                    // que el toque se vea en el momento y en los dos sentidos.
                    JSONArray a = o.optJSONArray("tareas");
                    for (int i = 0; a != null && i < a.length(); i++) {
                        Object it = a.opt(i);
                        String t = WidgetDatos.tareaTxt(it);
                        if (t.isEmpty()) continue;
                        JSONObject f = new JSONObject();
                        f.put("izq", t);
                        f.put("caja", true);
                        f.put("txt", t);
                        f.put("hecha", WidgetDatos.tareaVista(c, t, WidgetDatos.tareaHecha(it)));
                        out.put(f);
                    }
                    // Y las escritas en el widget que aun no han entrado en la
                    // app: se ven abajo, marcadas, y un toque las borra.
                    JSONArray nv = WidgetDatos.nuevasEnCola(c);
                    for (int i = 0; i < nv.length(); i++) {
                        String t = nv.optString(i, "");
                        if (t.isEmpty() || WidgetDatos.estaEnApp(c, t)) continue;
                        JSONObject f = new JSONObject();
                        f.put("izq", t);
                        f.put("caja", true);
                        f.put("txt", t);
                        f.put("hecha", false);
                        f.put("est", "sin volcar");
                        out.put(f);
                    }
                } else if ("material".equals(tipo)) {
                    JSONArray a = o.optJSONArray("material");
                    boolean puestoCorte = false;
                    for (int i = 0; a != null && i < a.length(); i++) {
                        JSONObject m = a.optJSONObject(i);
                        if (m == null) continue;
                        String id = m.optString("id", "");
                        if (id.isEmpty() || WidgetDatos.materialEnCola(c, id)) continue;
                        // separador entre la obra activa y el resto
                        if (!m.optBoolean("propia", false) && !puestoCorte) {
                            puestoCorte = true;
                            JSONObject sep = new JSONObject();
                            sep.put("izq", "OTRAS OBRAS");
                            sep.put("separador", true);
                            out.put(sep);
                        }
                        JSONObject f = new JSONObject();
                        f.put("izq", m.optString("desc", ""));
                        f.put("der", m.optString("cant", ""));
                        f.put("est", m.optString("falta", ""));
                        f.put("sub", m.optBoolean("propia", false) ? "" : m.optString("obra", ""));
                        f.put("caja", true);
                        f.put("id", id);
                        f.put("dia", m.optString("dia", ""));
                        out.put(f);
                    }
                } else {
                    JSONArray a = o.optJSONArray("vigilar");
                    for (int i = 0; a != null && i < a.length(); i++) {
                        JSONObject x = a.optJSONObject(i);
                        if (x == null) continue;
                        int r = x.optInt("rojos", 0);
                        JSONObject f = new JSONObject();
                        f.put("izq", x.optString("nom", ""));
                        f.put("sub", x.optString("crit", ""));
                        f.put("est", r + (r == 1 ? " rojo" : " rojos"));
                        f.put("aviso", x.optBoolean("aviso", false));
                        out.put(f);
                    }
                }
            } catch (Exception ignored) { }
            filas = out;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_fila_lista);
            JSONObject f = filas.optJSONObject(i);
            if (f == null) return v;

            boolean sep = f.optBoolean("separador", false);
            boolean caja = f.optBoolean("caja", false) && !sep;

            boolean hecha = f.optBoolean("hecha", false);

            v.setTextViewText(R.id.fIzq, f.optString("izq", ""));
            v.setViewVisibility(R.id.fCaja, caja ? android.view.View.VISIBLE : android.view.View.GONE);
            v.setInt(R.id.fCaja, "setBackgroundResource",
                    hecha ? R.drawable.widget_caja_ok : R.drawable.widget_caja);
            // Las banderas se ponen SIEMPRE: RemoteViews reaprovecha las filas y
            // si no se reponen, una fila hecha deja tachada a la que ocupe su sitio.
            v.setInt(R.id.fIzq, "setPaintFlags", hecha
                    ? (android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG)
                    : android.graphics.Paint.ANTI_ALIAS_FLAG);

            String sub = f.optString("sub", "");
            v.setTextViewText(R.id.fSub, sub);
            v.setViewVisibility(R.id.fSub, sub.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            String der = f.optString("der", "");
            v.setTextViewText(R.id.fDer, der);
            v.setViewVisibility(R.id.fDer, der.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            String est = f.optString("est", "");
            v.setTextViewText(R.id.fEst, est);
            v.setViewVisibility(R.id.fEst, est.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
            v.setTextColor(R.id.fEst, WidgetDatos.color(c,
                    f.optBoolean("aviso", false) ? R.color.w_rojo_claro : R.color.w_ambar));

            if (sep) {
                v.setTextColor(R.id.fIzq, WidgetDatos.color(c, R.color.w_flojo));
                v.setTextViewTextSize(R.id.fIzq, android.util.TypedValue.COMPLEX_UNIT_SP, 9f);
            } else {
                v.setTextColor(R.id.fIzq, WidgetDatos.color(c, hecha ? R.color.w_flojo : R.color.w_texto));
                v.setTextViewTextSize(R.id.fIzq, android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
            }

            if (!sep) {
                Intent rellena = new Intent();
                if (f.has("txt")) rellena.putExtra("txt", f.optString("txt"));
                if (f.has("id")) {
                    rellena.putExtra("id", f.optString("id"));
                    rellena.putExtra("dia", f.optString("dia"));
                }
                v.setOnClickFillInIntent(R.id.fFila, rellena);
            }
            return v;
        }

        @Override public int getViewTypeCount() { return 1; }
    }
}
