package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Deuda a nombre de un inmueble (RN-20). Valores en pesos enteros.
 * "saldo" baja con cada pago confirmado (RN-42).
 */
@Entity
@Table(name = "obligacion")
public class Obligacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConceptoObligacion concepto;

    // Periodo AAAA-MM al que pertenece.
    @Column(nullable = false)
    private String periodo;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private long valor;

    @Column(nullable = false)
    private long saldo;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoObligacion estado = EstadoObligacion.PENDIENTE;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Column(name = "creada_por")
    private String creadaPor;

    @Column(name = "anulada_en")
    private LocalDateTime anuladaEn;

    public Long getId() { return id; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public ConceptoObligacion getConcepto() { return concepto; }
    public void setConcepto(ConceptoObligacion concepto) { this.concepto = concepto; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public long getValor() { return valor; }
    public void setValor(long valor) { this.valor = valor; }
    public long getSaldo() { return saldo; }
    public void setSaldo(long saldo) { this.saldo = saldo; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public EstadoObligacion getEstado() { return estado; }
    public void setEstado(EstadoObligacion estado) { this.estado = estado; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public String getCreadaPor() { return creadaPor; }
    public void setCreadaPor(String creadaPor) { this.creadaPor = creadaPor; }
    public LocalDateTime getAnuladaEn() { return anuladaEn; }
    public void setAnuladaEn(LocalDateTime anuladaEn) { this.anuladaEn = anuladaEn; }
}