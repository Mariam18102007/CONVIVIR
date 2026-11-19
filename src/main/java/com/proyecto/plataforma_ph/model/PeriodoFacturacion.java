package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Configuración de la cuota de un periodo (formato AAAA-MM) y marca de generación. */
@Entity
@Table(name = "periodo_facturacion")
public class PeriodoFacturacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String periodo;

    // Valor base en pesos enteros.
    @Column(name = "valor_base", nullable = false)
    private long valorBase;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "generado_en")
    private LocalDateTime generadoEn;

    public Long getId() { return id; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public long getValorBase() { return valorBase; }
    public void setValorBase(long valorBase) { this.valorBase = valorBase; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public LocalDateTime getGeneradoEn() { return generadoEn; }
    public void setGeneradoEn(LocalDateTime generadoEn) { this.generadoEn = generadoEn; }
}