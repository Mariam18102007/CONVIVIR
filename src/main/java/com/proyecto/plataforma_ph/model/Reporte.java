package com.proyecto.plataforma_ph.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reporte")
public class Reporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaReporte categoria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "creador_id", nullable = false)
    private Integrante creador;

    // Opcional: un reporte puede no estar ligado a un inmueble.
    @ManyToOne
    @JoinColumn(name = "inmueble_id")
    private Inmueble inmueble;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    // Nombre del archivo guardado en la carpeta de uploads (lo genera el servidor).
    @Column(name = "evidencia_archivo")
    private String evidenciaArchivo;

    @Column(name = "evidencia_tipo")
    private String evidenciaTipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReporte estado = EstadoReporte.ABIERTO;

    @ManyToOne
    @JoinColumn(name = "responsable_id")
    private Integrante responsable;

    @Column(length = 1000)
    private String solucion;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    public Long getId() { return id; }
    public CategoriaReporte getCategoria() { return categoria; }
    public void setCategoria(CategoriaReporte categoria) { this.categoria = categoria; }
    public Integrante getCreador() { return creador; }
    public void setCreador(Integrante creador) { this.creador = creador; }
    public Inmueble getInmueble() { return inmueble; }
    public void setInmueble(Inmueble inmueble) { this.inmueble = inmueble; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getEvidenciaArchivo() { return evidenciaArchivo; }
    public void setEvidenciaArchivo(String evidenciaArchivo) { this.evidenciaArchivo = evidenciaArchivo; }
    public String getEvidenciaTipo() { return evidenciaTipo; }
    public void setEvidenciaTipo(String evidenciaTipo) { this.evidenciaTipo = evidenciaTipo; }
    public EstadoReporte getEstado() { return estado; }
    public void setEstado(EstadoReporte estado) { this.estado = estado; }
    public Integrante getResponsable() { return responsable; }
    public void setResponsable(Integrante responsable) { this.responsable = responsable; }
    public String getSolucion() { return solucion; }
    public void setSolucion(String solucion) { this.solucion = solucion; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getCerradoEn() { return cerradoEn; }
    public void setCerradoEn(LocalDateTime cerradoEn) { this.cerradoEn = cerradoEn; }
}