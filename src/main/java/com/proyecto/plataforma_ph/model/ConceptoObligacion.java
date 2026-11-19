package com.proyecto.plataforma_ph.model;

/**
 * Concepto de una obligación. Solo la cuota de administración es "ordinaria":
 * es la única que puede pagar un arrendatario (RN-22).
 */
public enum ConceptoObligacion {
    CUOTA_ADMINISTRACION("Cuota de administración", true),
    CUOTA_EXTRAORDINARIA("Cuota extraordinaria", false),
    MULTA("Multa", false),
    INTERES_MORA("Interés de mora", false),
    ALQUILER_ZONA("Alquiler de zona común", false);

    private final String etiqueta;
    private final boolean ordinaria;

    ConceptoObligacion(String etiqueta, boolean ordinaria) {
        this.etiqueta = etiqueta;
        this.ordinaria = ordinaria;
    }

    public String getEtiqueta() { return etiqueta; }
    public boolean isOrdinaria() { return ordinaria; }
}