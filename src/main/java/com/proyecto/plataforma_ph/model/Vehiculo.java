package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vehiculo")
public class Vehiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RN-14: única entre los vehículos activos (se valida en el controlador,
    // para poder volver a registrar una placa cuyo vehículo fue dado de baja).
    @Column(nullable = false)
    private String placa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVehiculo tipo;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String color;

    // RN-15: el vehículo pertenece al inmueble por cuya cuenta circula.
    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    // Dato secundario: propietario o arrendatario responsable (opcional).
    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Integrante responsable;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "motivo_baja")
    private String motivoBaja;

    public Long getId() { return id; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public TipoVehiculo getTipo() { return tipo; }
    public void setTipo(TipoVehiculo tipo) { this.tipo = tipo; }
    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public Integrante getResponsable() { return responsable; }
    public void setResponsable(Integrante responsable) { this.responsable = responsable; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public String getMotivoBaja() { return motivoBaja; }
    public void setMotivoBaja(String motivoBaja) { this.motivoBaja = motivoBaja; }
}
