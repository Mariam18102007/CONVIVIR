package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Entity
@Table(name = "inmueble")
public class Inmueble {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String identificacion;

    private BigDecimal coeficiente;

    private BigDecimal area;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Naturaleza naturaleza;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "tipo_inmueble_id", nullable = false)
    private TipoInmueble tipoInmueble;

    @ManyToOne
    @JoinColumn(name = "contenedor_id")
    private Inmueble contenedor;

    private boolean activo = true;

    @Column(name = "motivo_desactivacion")
    private String motivoDesactivacion;

    public Long getId() { return id; }
    public String getIdentificacion() { return identificacion; }
    public void setIdentificacion(String identificacion) { this.identificacion = identificacion; }
    public BigDecimal getCoeficiente() { return coeficiente; }
    public void setCoeficiente(BigDecimal coeficiente) { this.coeficiente = coeficiente; }
    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal area) { this.area = area; }
    public Naturaleza getNaturaleza() { return naturaleza; }
    public void setNaturaleza(Naturaleza naturaleza) { this.naturaleza = naturaleza; }
    public TipoInmueble getTipoInmueble() { return tipoInmueble; }
    public void setTipoInmueble(TipoInmueble tipoInmueble) { this.tipoInmueble = tipoInmueble; }
    public Inmueble getContenedor() { return contenedor; }
    public void setContenedor(Inmueble contenedor) { this.contenedor = contenedor; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getMotivoDesactivacion() { return motivoDesactivacion; }
    public void setMotivoDesactivacion(String motivoDesactivacion) { this.motivoDesactivacion = motivoDesactivacion; }
}