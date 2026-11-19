package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;

/** Configuración de un evento: a quién le llega y si está habilitado (RF-107). */
@Entity
@Table(name = "configuracion_notificacion")
public class ConfiguracionNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "evento", nullable = false, unique = true, length = 40)
    private EventoNotificacion evento;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario", nullable = false, length = 30)
    private DestinatarioNotificacion destinatario;

    @Column(name = "habilitado", nullable = false)
    private boolean habilitado = true;

    public Long getId() { return id; }
    public EventoNotificacion getEvento() { return evento; }
    public void setEvento(EventoNotificacion evento) { this.evento = evento; }
    public DestinatarioNotificacion getDestinatario() { return destinatario; }
    public void setDestinatario(DestinatarioNotificacion destinatario) { this.destinatario = destinatario; }
    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
}