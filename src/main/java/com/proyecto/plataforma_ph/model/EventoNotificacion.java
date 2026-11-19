package com.proyecto.plataforma_ph.model;

/**
 * Eventos que pueden generar una notificación (CU-17):
 * vencimientos, pagos, reportes, visitantes y autorizaciones.
 * Los avisos de cobro van al obligado del pago (RN-28);
 * los operativos, a los residentes del inmueble (RN-29).
 */
public enum EventoNotificacion {
    VENCIMIENTO_PROXIMO("Vencimiento próximo", "Vencimientos",
            "Una obligación está por vencer.", true, DestinatarioNotificacion.OBLIGADO_PAGO),
    OBLIGACION_VENCIDA("Obligación vencida", "Vencimientos",
            "Una obligación pasó su fecha de vencimiento sin pagarse.", true, DestinatarioNotificacion.OBLIGADO_PAGO),
    PAGO_CONFIRMADO("Pago confirmado", "Pagos",
            "Se confirmó un pago o abono del inmueble.", true, DestinatarioNotificacion.OBLIGADO_PAGO),
    REPORTE_ACTUALIZADO("Reporte actualizado", "Reportes",
            "Cambió el estado de un reporte del inmueble.", false, DestinatarioNotificacion.RESIDENTES),
    VISITANTE_INGRESO("Ingreso de visitante", "Visitantes",
            "Un visitante autorizado ingresó al conjunto.", false, DestinatarioNotificacion.RESIDENTES),
    AUTORIZACION_RECURRENTE("Autorización recurrente", "Autorizaciones",
            "Un autorizado recurrente ingresó o su autorización cambió.", false, DestinatarioNotificacion.RESIDENTES);

    private final String etiqueta;
    private final String grupo;
    private final String descripcion;
    private final boolean cobro;
    private final DestinatarioNotificacion porDefecto;

    EventoNotificacion(String etiqueta, String grupo, String descripcion, boolean cobro,
                       DestinatarioNotificacion porDefecto) {
        this.etiqueta = etiqueta;
        this.grupo = grupo;
        this.descripcion = descripcion;
        this.cobro = cobro;
        this.porDefecto = porDefecto;
    }

    public String getEtiqueta() { return etiqueta; }
    public String getGrupo() { return grupo; }
    public String getDescripcion() { return descripcion; }
    /** true: aviso de cobro (RN-28). false: aviso operativo (RN-29). */
    public boolean isCobro() { return cobro; }
    public DestinatarioNotificacion getPorDefecto() { return porDefecto; }
}