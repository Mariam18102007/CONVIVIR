package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_acceso")
public class MovimientoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vacío cuando el ingreso fue sin autorización previa (RN-12).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizacion_id")
    private Autorizacion autorizacion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String documento;

    // Solo para ingresos sin autorización previa.
    private String motivo;

    private String placa;

    @Column(name = "ingreso_en", nullable = false)
    private LocalDateTime ingresoEn;

    @Column(name = "salida_en")
    private LocalDateTime salidaEn;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "vigilante_ingreso_id", nullable = false)
    private Integrante vigilanteIngreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vigilante_salida_id")
    private Integrante vigilanteSalida;

    public Long getId() { return id; }
    public Autorizacion getAutorizacion() { return autorizacion; }
    public void setAutorizacion(Autorizacion autorizacion) { this.autorizacion = autorizacion; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public LocalDateTime getIngresoEn() { return ingresoEn; }
    public void setIngresoEn(LocalDateTime ingresoEn) { this.ingresoEn = ingresoEn; }
    public LocalDateTime getSalidaEn() { return salidaEn; }
    public void setSalidaEn(LocalDateTime salidaEn) { this.salidaEn = salidaEn; }
    public Integrante getVigilanteIngreso() { return vigilanteIngreso; }
    public void setVigilanteIngreso(Integrante vigilanteIngreso) { this.vigilanteIngreso = vigilanteIngreso; }
    public Integrante getVigilanteSalida() { return vigilanteSalida; }
    public void setVigilanteSalida(Integrante vigilanteSalida) { this.vigilanteSalida = vigilanteSalida; }
}