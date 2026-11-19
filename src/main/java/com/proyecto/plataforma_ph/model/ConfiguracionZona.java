package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;

/**
 * Tarifa y horario de una zona común (un inmueble cuyo tipo permite reserva).
 * El horario se guarda en minutos desde la medianoche (8:00 = 480).
 */
@Entity
@Table(name = "configuracion_zona", uniqueConstraints = @UniqueConstraint(columnNames = "zona_id"))
public class ConfiguracionZona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zona_id", nullable = false)
    private Inmueble zona;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_cobro", nullable = false)
    private UnidadCobro unidadCobro;

    @Column(nullable = false)
    private long tarifa;

    @Column(name = "apertura_min", nullable = false)
    private int aperturaMin;

    @Column(name = "cierre_min", nullable = false)
    private int cierreMin;

    public Long getId() { return id; }
    public Inmueble getZona() { return zona; }
    public void setZona(Inmueble zona) { this.zona = zona; }
    public UnidadCobro getUnidadCobro() { return unidadCobro; }
    public void setUnidadCobro(UnidadCobro unidadCobro) { this.unidadCobro = unidadCobro; }
    public long getTarifa() { return tarifa; }
    public void setTarifa(long tarifa) { this.tarifa = tarifa; }
    public int getAperturaMin() { return aperturaMin; }
    public void setAperturaMin(int aperturaMin) { this.aperturaMin = aperturaMin; }
    public int getCierreMin() { return cierreMin; }
    public void setCierreMin(int cierreMin) { this.cierreMin = cierreMin; }
}