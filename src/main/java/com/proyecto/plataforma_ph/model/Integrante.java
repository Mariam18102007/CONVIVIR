package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
@Table(name = "integrante")
public class Integrante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolIntegrante rol;

    @NotBlank
    @Column(name = "tipo_documento", nullable = false)
    private String tipoDocumento;

    @NotBlank
    @Column(name = "numero_documento", nullable = false, unique = true)
    private String numeroDocumento;

    @NotBlank
    @Column(nullable = false)
    private String nombres;

    @NotBlank
    @Column(nullable = false)
    private String apellidos;

    private String telefono;
    private String correo;

    @Column(nullable = false)
    private boolean residente;

    
    private String genero;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "razon_social")
    private String razonSocial;

    
    @Column(name = "numero_contrato")
    private String numeroContrato;
    private String parentesco;
    private String cargo;

    @Column(name = "periodo_consejo")
    private String periodoConsejo;

    @Column(name = "empresa_seguridad")
    private String empresaSeguridad;

    private String turno;

    private boolean activo = true;

    @Column(name = "motivo_desactivacion")
    private String motivoDesactivacion;

    public Long getId() { return id; }
    public RolIntegrante getRol() { return rol; }
    public void setRol(RolIntegrante rol) { this.rol = rol; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }
    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public boolean isResidente() { return residente; }
    public void setResidente(boolean residente) { this.residente = residente; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getNumeroContrato() { return numeroContrato; }
    public void setNumeroContrato(String numeroContrato) { this.numeroContrato = numeroContrato; }
    public String getParentesco() { return parentesco; }
    public void setParentesco(String parentesco) { this.parentesco = parentesco; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getPeriodoConsejo() { return periodoConsejo; }
    public void setPeriodoConsejo(String periodoConsejo) { this.periodoConsejo = periodoConsejo; }
    public String getEmpresaSeguridad() { return empresaSeguridad; }
    public void setEmpresaSeguridad(String empresaSeguridad) { this.empresaSeguridad = empresaSeguridad; }
    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getMotivoDesactivacion() { return motivoDesactivacion; }
    public void setMotivoDesactivacion(String motivoDesactivacion) { this.motivoDesactivacion = motivoDesactivacion; }
}