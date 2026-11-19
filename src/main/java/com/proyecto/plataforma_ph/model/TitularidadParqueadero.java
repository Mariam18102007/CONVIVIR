package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "titularidad_parqueadero")
public class TitularidadParqueadero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "parqueadero_id", nullable = false)
    private Inmueble parqueadero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "titular_id", nullable = false)
    private Inmueble titular;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Vacía mientras sea el titular vigente.
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    public Long getId() { return id; }
    public Inmueble getParqueadero() { return parqueadero; }
    public void setParqueadero(Inmueble parqueadero) { this.parqueadero = parqueadero; }
    public Inmueble getTitular() { return titular; }
    public void setTitular(Inmueble titular) { this.titular = titular; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
}