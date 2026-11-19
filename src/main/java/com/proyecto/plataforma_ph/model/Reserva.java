package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reserva de una zona común (CU-20 y CU-21).
 * El valor se fija al solicitar; al confirmar se genera la obligación ALQUILER_ZONA (RN-39).
 */
@Entity
@Table(name = "reserva")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zona_id", nullable = false)
    private Inmueble zona;

    @ManyToOne(optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Integrante solicitante;

    // Inmueble al que se imputa la obligación (RF-116).
    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    @Column(name = "fin", nullable = false)
    private LocalDateTime fin;

    @Column(nullable = false, length = 200)
    private String finalidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado = EstadoReserva.SOLICITADA;

    @Column(nullable = false)
    private long valor;

    // Obligación ALQUILER_ZONA generada al confirmar.
    @ManyToOne
    @JoinColumn(name = "obligacion_id")
    private Obligacion obligacion;

    // Motivo de rechazo o de cancelación.
    @Column(name = "motivo_cierre", length = 200)
    private String motivoCierre;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "confirmada_en")
    private LocalDateTime confirmadaEn;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    @Column(name = "fecha_finalizacion")
    private LocalDate fechaFinalizacion;

    // Multa por incumplimiento (RF-122).
    @ManyToOne
    @JoinColumn(name = "multa_id")
    private Obligacion multa;

    @Column(name = "motivo_multa", length = 200)
    private String motivoMulta;

    public Long getId() { return id; }
    public Inmueble getZona() { return zona; }
    public void setZona(Inmueble zona) { this.zona = zona; }
    public Integrante getSolicitante() { return solicitante; }
    public void setSolicitante(Integrante solicitante) { this.solicitante = solicitante; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public LocalDateTime getInicio() { return inicio; }
    public void setInicio(LocalDateTime inicio) { this.inicio = inicio; }
    public LocalDateTime getFin() { return fin; }
    public void setFin(LocalDateTime fin) { this.fin = fin; }
    public String getFinalidad() { return finalidad; }
    public void setFinalidad(String finalidad) { this.finalidad = finalidad; }
    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }
    public long getValor() { return valor; }
    public void setValor(long valor) { this.valor = valor; }
    public Obligacion getObligacion() { return obligacion; }
    public void setObligacion(Obligacion obligacion) { this.obligacion = obligacion; }
    public String getMotivoCierre() { return motivoCierre; }
    public void setMotivoCierre(String motivoCierre) { this.motivoCierre = motivoCierre; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public LocalDateTime getConfirmadaEn() { return confirmadaEn; }
    public void setConfirmadaEn(LocalDateTime confirmadaEn) { this.confirmadaEn = confirmadaEn; }
    public LocalDateTime getCerradaEn() { return cerradaEn; }
    public void setCerradaEn(LocalDateTime cerradaEn) { this.cerradaEn = cerradaEn; }
    public LocalDate getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(LocalDate fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
    public Obligacion getMulta() { return multa; }
    public void setMulta(Obligacion multa) { this.multa = multa; }
    public String getMotivoMulta() { return motivoMulta; }
    public void setMotivoMulta(String motivoMulta) { this.motivoMulta = motivoMulta; }
}