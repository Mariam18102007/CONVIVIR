package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entrada y salida de un vehículo del conjunto (una fila por permanencia).
 * Si el vehículo está registrado, "vehiculo" apunta a él; si es de un visitante,
 * queda vacío y solo se guarda la placa.
 */
@Entity
@Table(name = "movimiento_vehicular")
public class MovimientoVehicular {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(nullable = false)
    private String placa;

    // Inmueble del vehículo, o inmueble que visita el visitante.
    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    // Parqueadero que ocupa (opcional).
    @ManyToOne
    @JoinColumn(name = "parqueadero_id")
    private Inmueble parqueadero;

    @Column(name = "ingreso_en", nullable = false)
    private LocalDateTime ingresoEn;

    @Column(name = "salida_en")
    private LocalDateTime salidaEn;

    @Column(name = "vigilante_ingreso")
    private String vigilanteIngreso;

    @Column(name = "vigilante_salida")
    private String vigilanteSalida;

    public Long getId() { return id; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public void setVehiculo(Vehiculo vehiculo) { this.vehiculo = vehiculo; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public Inmueble getParqueadero() { return parqueadero; }
    public void setParqueadero(Inmueble parqueadero) { this.parqueadero = parqueadero; }
    public LocalDateTime getIngresoEn() { return ingresoEn; }
    public void setIngresoEn(LocalDateTime ingresoEn) { this.ingresoEn = ingresoEn; }
    public LocalDateTime getSalidaEn() { return salidaEn; }
    public void setSalidaEn(LocalDateTime salidaEn) { this.salidaEn = salidaEn; }
    public String getVigilanteIngreso() { return vigilanteIngreso; }
    public void setVigilanteIngreso(String vigilanteIngreso) { this.vigilanteIngreso = vigilanteIngreso; }
    public String getVigilanteSalida() { return vigilanteSalida; }
    public void setVigilanteSalida(String vigilanteSalida) { this.vigilanteSalida = vigilanteSalida; }
}