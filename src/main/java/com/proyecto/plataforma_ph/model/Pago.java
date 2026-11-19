package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pago de una o varias obligaciones de un mismo inmueble.
 * Presencial: se confirma al registrarse (RN-24). Pasarela: queda PENDIENTE hasta que
 * la pasarela devuelve un resultado válido (RN-23). Cada pago confirmado baja el saldo (RN-42).
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalPago canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedioPago medio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String pagador;

    // Correo del usuario que registró o inició el pago.
    @Column(name = "registrado_por")
    private String registradoPor;

    // Referencia que se envía a la pasarela (solo pagos por pasarela).
    @Column(unique = true)
    private String referencia;

    // Estado que reporta la pasarela simulada (APROBADA o RECHAZADA).
    @Column(name = "estado_pasarela")
    private String estadoPasarela;

    @Column(name = "transaccion_id")
    private String transaccionId;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PagoDetalle> detalles = new ArrayList<>();

    public Long getId() { return id; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public CanalPago getCanal() { return canal; }
    public void setCanal(CanalPago canal) { this.canal = canal; }
    public MedioPago getMedio() { return medio; }
    public void setMedio(MedioPago medio) { this.medio = medio; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getPagador() { return pagador; }
    public void setPagador(String pagador) { this.pagador = pagador; }
    public String getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(String registradoPor) { this.registradoPor = registradoPor; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getEstadoPasarela() { return estadoPasarela; }
    public void setEstadoPasarela(String estadoPasarela) { this.estadoPasarela = estadoPasarela; }
    public String getTransaccionId() { return transaccionId; }
    public void setTransaccionId(String transaccionId) { this.transaccionId = transaccionId; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(LocalDateTime confirmadoEn) { this.confirmadoEn = confirmadoEn; }
    public LocalDateTime getAnuladoEn() { return anuladoEn; }
    public void setAnuladoEn(LocalDateTime anuladoEn) { this.anuladoEn = anuladoEn; }
    public List<PagoDetalle> getDetalles() { return detalles; }
}