package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Aviso entregado a un integrante, con su resultado (RN-30). */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "evento", nullable = false, length = 40)
    private EventoNotificacion evento;

    // Nulo cuando el resultado es SIN_DESTINATARIO.
    @ManyToOne
    @JoinColumn(name = "destinatario_id")
    private Integrante destinatario;

    @ManyToOne
    @JoinColumn(name = "inmueble_id")
    private Inmueble inmueble;

    @Column(name = "asunto", nullable = false, length = 150)
    private String asunto;

    @Column(name = "mensaje", nullable = false, length = 500)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 30)
    private ResultadoNotificacion resultado;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "leida", nullable = false)
    private boolean leida = false;

    @Column(name = "leida_en")
    private LocalDateTime leidaEn;

    public Long getId() { return id; }
    public EventoNotificacion getEvento() { return evento; }
    public void setEvento(EventoNotificacion evento) { this.evento = evento; }
    public Integrante getDestinatario() { return destinatario; }
    public void setDestinatario(Integrante destinatario) { this.destinatario = destinatario; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public ResultadoNotificacion getResultado() { return resultado; }
    public void setResultado(ResultadoNotificacion resultado) { this.resultado = resultado; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
    public boolean isLeida() { return leida; }
    public void setLeida(boolean leida) { this.leida = leida; }
    public LocalDateTime getLeidaEn() { return leidaEn; }
    public void setLeidaEn(LocalDateTime leidaEn) { this.leidaEn = leidaEn; }
}