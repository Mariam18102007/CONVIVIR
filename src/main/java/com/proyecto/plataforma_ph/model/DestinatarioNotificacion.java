package com.proyecto.plataforma_ph.model;

/**
 * A quién le llega un aviso (RF-107).
 * OBLIGADO_PAGO: propietario con vinculación vigente (RN-28).
 * RESIDENTES: propietarios residentes y arrendatarios vigentes (RN-29).
 * TODOS_VINCULADOS: cualquier integrante con vinculación vigente al inmueble.
 */
public enum DestinatarioNotificacion {
    OBLIGADO_PAGO("Propietario obligado del pago"),
    RESIDENTES("Propietarios residentes y arrendatarios"),
    TODOS_VINCULADOS("Todos los vinculados vigentes");

    private final String etiqueta;

    DestinatarioNotificacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
