package com.miparte.montajes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * LA LIBRETA, para editarla desde el widget sin abrir la app.
 *
 * NO es una lista y aqui no se «añaden» notas: se abre la hoja tal como esta,
 * se cambia lo que sea y se guarda entera. Es lo mismo que hace la app.
 *
 * Un widget no puede escribir en los datos de la app, asi que lo escrito se
 * queda en la cola y entra sola la proxima vez que se abra. Se guarda junto
 * con la BASE (lo que habia al abrir): si mientras tanto la hoja ha cambiado
 * en la app, esta no la pisa, pega lo nuevo al final. Perder notas seria mucho
 * peor que repetir un parrafo.
 */
public class NotaRapida extends Activity {

    private AlertDialog dialogo;
    private EditText campo;
    private String base = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        base = WidgetDatos.libretaDeLaApp(this);
        String ahora = WidgetDatos.libreta(this);   // con lo pendiente, si lo hay

        campo = new EditText(this);
        campo.setHint("Toca y escribe lo que quieras");
        campo.setMinLines(6);
        campo.setMaxLines(14);
        campo.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        campo.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        campo.setText(ahora);
        campo.setSelection(campo.getText().length());   // el cursor, al final

        FrameLayout caja = new FrameLayout(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        caja.setPadding(p, p / 2, p, 0);
        caja.addView(campo);

        dialogo = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Notas")
                .setView(caja)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { guarda(); }
                })
                .setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { finish(); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface d) { finish(); }
                })
                .create();

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialogo.show();
    }

    private void guarda() {
        String t = campo.getText().toString();
        // Se guarda aunque quede vacia: borrar la hoja tambien es editarla.
        WidgetDatos.guardaLibreta(this, base, t);
        Widgets.refresca(this);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (dialogo != null && dialogo.isShowing()) dialogo.dismiss();
        dialogo = null;
        super.onDestroy();
    }
}
