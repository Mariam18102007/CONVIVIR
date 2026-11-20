package com.proyecto.plataforma_ph.model;

/**
 * Concepto de una obligación.
 * ordinaria: expensa común ordinaria; solo la cuota de administración (RN-22).
 * pagableArrendatario: lo que un arrendatario puede ver y pagar. Incluye el alquiler de
 * una zona común (cobro voluntario, no expensa), pero no multas, extraordinarias ni mora.
 */
public enum ConceptoObligacion {
    CUOTA_ADMINISTRACION("Cuota de administración", true, true),
    CUOTA_EXTRAORDINARIA("Cuota extraordinaria", false, false),
    MULTA("Multa", false, false),
    INTERES_MORA("Interés de mora", false, false),
    ALQUILER_ZONA("Alquiler de zona común", false, true);

    private final String etiqueta;
    private final boolean ordinaria;
    private final boolean pagableArrendatario;

    ConceptoObligacion(String etiqueta, boolean ordinaria, boolean pagableArrendatario) {
        this.etiqueta = etiqueta;
        this.ordinaria = ordinaria;
        this.pagableArrendatario = pagableArrendatario;
    }

    public String getEtiqueta() { return etiqueta; }
    public boolean isOrdinaria() { return ordinaria; }
    public boolean isPagableArrendatario() { return pagableArrendatario; }
}