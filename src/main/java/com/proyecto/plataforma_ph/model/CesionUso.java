package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Un inmueble titular permite que otro inmueble use su parqueadero (sección 7.3). */
@Entity
@Table(name = "cesion_uso")
public class CesionUso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "parqueadero_id", nullable = false)
    private Inmueble parqueadero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_titular_id", nullable = false)
    private Inmueble inmuebleTitular;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_usuario_id", nullable = false)
    private Inmueble inmuebleUsuario;

    // Opcional: vehículo amparado por la cesión.
    @ManyToOne
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Vacía si la cesión no tiene fecha de terminación todavía.
    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCesion estado = EstadoCesion.VIGENTE;

    // RN-35: para bienes comunes de uso exclusivo.
    @Column(name = "autorizada_asamblea", nullable = false)
    private boolean autorizadaAsamblea;

    // ¿Está en uso en esta fecha? (vigente y dentro de su periodo)
    public boolean enCurso(LocalDate hoy) {
        return estado == EstadoCesion.VIGENTE
                && !fechaInicio.isAfter(hoy)
                && (fechaFin == null || !fechaFin.isBefore(hoy));
    }

    public Long getId() { return id; }
    public Inmueble getParqueadero() { return parqueadero; }
    public void setParqueadero(Inmueble parqueadero) { this.parqueadero = parqueadero; }
    public Inmueble getInmuebleTitular() { return inmuebleTitular; }
    public void setInmuebleTitular(Inmueble inmuebleTitular) { this.inmuebleTitular = inmuebleTitular; }
    public Inmueble getInmuebleUsuario() { return inmuebleUsuario; }
    public void setInmuebleUsuario(Inmueble inmuebleUsuario) { this.inmuebleUsuario = inmuebleUsuario; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public void setVehiculo(Vehiculo vehiculo) { this.vehiculo = vehiculo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public EstadoCesion getEstado() { return estado; }
    public void setEstado(EstadoCesion estado) { this.estado = estado; }
    public boolean isAutorizadaAsamblea() { return autorizadaAsamblea; }
    public void setAutorizadaAsamblea(boolean autorizadaAsamblea) { this.autorizadaAsamblea = autorizadaAsamblea; }
}