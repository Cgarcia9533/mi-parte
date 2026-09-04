package com.miparte.montajes;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Al poner el widget de listas en la pantalla, Android abre esto primero:
 * una ventanita con las listas que tienes en la app para elegir cual ensena
 * ese widget. Se puede poner uno por lista.
 *
 * Si se cierra sin elegir, el widget no se coloca (RESULT_CANCELED), que es
 * como Android espera que se porte una pantalla de configuracion.
 */
public class ConfigLista extends Activity {

    private int wid = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AlertDialog dialogo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);

        Intent it = getIntent();
        if (it != null && it.getExtras() != null) {
            wid = it.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }

        final JSONArray listas = WidgetDatos.listas(this);
        final String[] ids = new String[listas.length()];
        final String[] nombres = new String[listas.length()];
        for (int i = 0; i < listas.length(); i++) {
            JSONObject o = listas.optJSONObject(i);
            ids[i] = o == null ? "tareas" : o.optString("id", "tareas");
            nombres[i] = o == null ? "Pendientes" : o.optString("nom", "Lista");
        }

        // Sin listas no hay nada que elegir: se coloca con Pendientes y se dice.
        if (ids.length == 1) { elige(ids[0]); return; }

        dialogo = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("¿Qué lista pongo?")
                .setItems(nombres, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { elige(ids[w]); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface d) { finish(); }
                })
                .create();
        dialogo.show();
    }

    private void elige(String lid) {
        WidgetDatos.guardaLista(this, wid, lid);
        AppWidgetManager m = AppWidgetManager.getInstance(this);
        if (m != null) {
            m.updateAppWidget(wid, WidgetTareas.pinta(this, wid));
            m.notifyAppWidgetViewDataChanged(new int[]{ wid }, R.id.wLista);
        }
        Intent ok = new Intent();
        ok.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid);
        setResult(RESULT_OK, ok);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (dialogo != null && dialogo.isShowing()) dialogo.dismiss();
        dialogo = null;
        super.onDestroy();
    }
}
