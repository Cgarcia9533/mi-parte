package com.miparte.montajes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Tarea nueva desde el widget, sin abrir la app.
 *
 * Es una ventanita encima de lo que tengas puesto. Al aceptar, la tarea NO
 * entra en la app en ese momento: la app web esta cerrada y es ella la que
 * guarda. Se queda en la cola, se ve ya en el widget marcada como «sin volcar»
 * y entra sola la proxima vez que abras la app, igual que los fichajes y las
 * fotos rapidas.
 *
 * La ventana no se cierra al añadir: se vacia y se queda esperando, para
 * apuntar tres cosas seguidas sin tener que volver a tocar el widget. Se cierra
 * con Cerrar o con el boton de atras.
 */
public class TareaRapida extends Activity {

    private AlertDialog dialogo;
    private EditText campo;
    private int puestas = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        campo = new EditText(this);
        campo.setHint("Qué hay que hacer");
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        campo.setImeOptions(EditorInfo.IME_ACTION_DONE);
        campo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_DONE) { apunta(); return true; }
                return false;
            }
        });

        FrameLayout caja = new FrameLayout(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        caja.setPadding(p, p / 2, p, 0);
        caja.addView(campo);

        dialogo = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Nueva tarea")
                .setView(caja)
                .setPositiveButton("Añadir", null)   // null: lo enganchamos abajo
                .setNegativeButton("Cerrar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { finish(); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface d) { finish(); }
                })
                .create();

        // El boton se engancha a mano y NO cierra la ventana: asi se pueden
        // apuntar varias seguidas. Con setPositiveButton normal, Android la
        // cierra siempre al pulsar.
        dialogo.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                dialogo.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new android.view.View.OnClickListener() {
                            public void onClick(android.view.View v) { apunta(); }
                        });
            }
        });

        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialogo.show();
    }

    private void apunta() {
        String t = campo.getText().toString().trim();
        if (t.isEmpty()) return;
        WidgetDatos.nuevaTarea(this, t);
        Widgets.refresca(this);
        puestas++;
        campo.setText("");
        dialogo.setTitle(puestas == 1 ? "Nueva tarea · 1 apuntada"
                : "Nueva tarea · " + puestas + " apuntadas");
    }

    @Override
    protected void onDestroy() {
        if (dialogo != null && dialogo.isShowing()) dialogo.dismiss();
        dialogo = null;
        super.onDestroy();
    }
}
