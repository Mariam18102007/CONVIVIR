package com.proyecto.plataforma_ph.model;

/** Unidad con la que se cobra el alquiler de una zona común (RN-39). */
public enum UnidadCobro {
    HORA("Por hora"),
    DIA("Por día"),
    EVENTO("Por evento");

    private final String etiqueta;

    UnidadCobro(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}