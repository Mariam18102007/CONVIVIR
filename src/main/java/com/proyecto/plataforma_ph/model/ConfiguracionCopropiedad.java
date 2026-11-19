package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;

/** Datos de la propiedad horizontal (RF-106). Siempre hay una sola fila, con id = 1. */
@Entity
@Table(name = "configuracion_copropiedad")
public class ConfiguracionCopropiedad {

    @Id
    private Long id = 1L;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 30)
    private String nit;

    @Column(length = 200)
    private String direccion;

    @Column(length = 30)
    private String telefono;

    @Column(length = 120)
    private String correo;

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}