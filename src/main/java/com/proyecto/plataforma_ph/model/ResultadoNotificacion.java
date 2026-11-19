package com.proyecto.plataforma_ph.model;

/** Resultado del envío (RN-30). */
public enum ResultadoNotificacion {
    ENTREGADA("Entregada"),
    SIN_DESTINATARIO("Sin destinatario");

    private final String etiqueta;

    ResultadoNotificacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}