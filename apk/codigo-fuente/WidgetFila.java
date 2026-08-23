package com.miparte.montajes;

/** PARTE DE HOY, en fila 4x1. Sin la barra de dos botones. */
public class WidgetFila extends Widget {
    @Override
    int layout() { return R.layout.widget_fila; }

    @Override
    boolean conBotones() { return false; }
}
