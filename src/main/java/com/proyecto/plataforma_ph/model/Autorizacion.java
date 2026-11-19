package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "autorizacion")
public class Autorizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RN-08: toda autorización pertenece a un inmueble...
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inmueble_id", nullable = false)
    private Inmueble inmueble;

    // ...y la otorga un propietario residente o arrendatario con vinculación vigente.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "autorizante_id", nullable = false)
    private Integrante autorizante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAutorizacion tipo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String documento;

    // Solo para autorizaciones recurrentes (paso 2).
    private String motivo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "usa_parqueadero_visitantes", nullable = false)
    private boolean usaParqueaderoVisitantes;

    // Solo recurrentes: días de la semana (ej. "MARTES,JUEVES") y franja horaria.
    @Column(name = "dias_semana")
    private String diasSemana;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAutorizacion estado = EstadoAutorizacion.VIGENTE;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();

    // RN-09: pierde validez sola al vencer su vigencia; no se modifica la fila.
    public EstadoAutorizacion estadoEfectivo() {
        if (estado == EstadoAutorizacion.VIGENTE && fechaFin.isBefore(LocalDate.now())) {
            return EstadoAutorizacion.VENCIDA;
        }
        return estado;
    }

    public Long getId() { return id; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public Integrante getAutorizante() { return autorizante; }
    public void setAutorizante(Integrante autorizante) { this.autorizante = autorizante; }
    public TipoAutorizacion getTipo() { return tipo; }
    public void setTipo(TipoAutorizacion tipo) { this.tipo = tipo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public boolean isUsaParqueaderoVisitantes() { return usaParqueaderoVisitantes; }
    public void setUsaParqueaderoVisitantes(boolean v) { this.usaParqueaderoVisitantes = v; }
    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }
    public EstadoAutorizacion getEstado() { return estado; }
    public void setEstado(EstadoAutorizacion estado) { this.estado = estado; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
}