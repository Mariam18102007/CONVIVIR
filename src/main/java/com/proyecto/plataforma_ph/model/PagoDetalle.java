package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;

/** Valor de un pago aplicado a una obligación (permite abonos parciales). */
@Entity
@Table(name = "pago_detalle")
public class PagoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @ManyToOne(optional = false)
    @JoinColumn(name = "obligacion_id", nullable = false)
    private Obligacion obligacion;

    @Column(nullable = false)
    private long valor;

    public Long getId() { return id; }
    public Pago getPago() { return pago; }
    public void setPago(Pago pago) { this.pago = pago; }
    public Obligacion getObligacion() { return obligacion; }
    public void setObligacion(Obligacion obligacion) { this.obligacion = obligacion; }
    public long getValor() { return valor; }
    public void setValor(long valor) { this.valor = valor; }
}