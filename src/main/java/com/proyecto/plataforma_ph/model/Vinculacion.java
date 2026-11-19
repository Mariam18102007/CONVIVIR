package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "vinculacion")
public class Vinculacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "integrante_id", nullable = false)
    private Integrante integrante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vinculo", nullable = false)
    private TipoVinculo tipoVinculo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVinculacion estado = EstadoVinculacion.VIGENTE;

    @Column(name = "motivo_correccion")
    private String motivoCorreccion;

    public Long getId() { return id; }
    public Integrante getIntegrante() { return integrante; }
    public void setIntegrante(Integrante integrante) { this.integrante = integrante; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public TipoVinculo getTipoVinculo() { return tipoVinculo; }
    public void setTipoVinculo(TipoVinculo tipoVinculo) { this.tipoVinculo = tipoVinculo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public EstadoVinculacion getEstado() { return estado; }
    public void setEstado(EstadoVinculacion estado) { this.estado = estado; }
    public String getMotivoCorreccion() { return motivoCorreccion; }
    public void setMotivoCorreccion(String motivoCorreccion) { this.motivoCorreccion = motivoCorreccion; }
}