package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "tipo_inmueble")
public class TipoInmueble {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String nombre;

    private String descripcion;

    @Column(name = "permite_cesion", nullable = false)
    private boolean permiteCesion;

    @Column(name = "permite_reserva", nullable = false)
    private boolean permiteReserva;

    @Column(name = "genera_cuota", nullable = false)
    private boolean generaCuota;

    private boolean activo = true;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public boolean isPermiteCesion() { return permiteCesion; }
    public void setPermiteCesion(boolean permiteCesion) { this.permiteCesion = permiteCesion; }
    public boolean isPermiteReserva() { return permiteReserva; }
    public void setPermiteReserva(boolean permiteReserva) { this.permiteReserva = permiteReserva; }
    public boolean isGeneraCuota() { return generaCuota; }
    public void setGeneraCuota(boolean generaCuota) { this.generaCuota = generaCuota; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
