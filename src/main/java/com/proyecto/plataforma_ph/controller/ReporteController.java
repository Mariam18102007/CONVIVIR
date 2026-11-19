package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.*;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-15 (crear y consultar reportes: propietario, arrendatario, vigilante) y
 * CU-16 (gestionar reportes: administrador).
 * La seguridad por rol está en SecurityConfig; aquí se valida el alcance (RN-07, RN-33).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<TipoVinculo> TIPOS_RESIDENTE =
            Set.of(TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.ARRENDATARIO);

    // Quién puede ser responsable de un reporte (RF-096).
    private static final Set<RolIntegrante> ROLES_RESPONSABLE =
            Set.of(RolIntegrante.ADMINISTRADOR, RolIntegrante.VIGILANTE);

    public record NuevoReporte(Long categoriaId, String descripcion, Long inmuebleId) {}

    public record AsignacionResponsable(Long responsableId) {}

    public record NuevaObservacion(String observacion) {}

    public record CierreReporte(String solucion) {}

    public record Opcion(Long id, String nombre) {}

    public record SeguimientoDto(Long id, LocalDateTime fecha, String usuario, String estado, String observacion) {}

    public record ReporteDto(Long id, Long categoriaId, String categoria, String descripcion, String estado,
                             Long inmuebleId, String inmueble, String creadoPor, LocalDateTime creadoEn,
                             boolean tieneEvidencia, String solucion, LocalDateTime cerradoEn,
                             Long responsableId, String responsable, List<SeguimientoDto> seguimiento) {}

    private final ReporteRepository reportes;
    private final CategoriaReporteRepository categorias;
    private final SeguimientoReporteRepository seguimientos;
    private final InmuebleRepository inmuebles;
    private final IntegranteRepository integrantes;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;
    private final String uploadsDir;

    public ReporteController(ReporteRepository reportes, CategoriaReporteRepository categorias,
                             SeguimientoReporteRepository seguimientos, InmuebleRepository inmuebles,
                             IntegranteRepository integrantes, VinculacionRepository vinculaciones,
                             UsuarioRepository usuarios, AuditoriaService auditoria,
                             NotificacionService notificaciones,
                             @Value("${app.uploads.dir:./uploads}") String uploadsDir) {
        this.reportes = reportes;
        this.categorias = categorias;
        this.seguimientos = seguimientos;
        this.inmuebles = inmuebles;
        this.integrantes = integrantes;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
        this.uploadsDir = uploadsDir;
    }

    // ---------- CU-15: propietario, arrendatario y vigilante ----------

    // RF-090
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReporteDto crear(@RequestBody NuevoReporte body, Authentication auth) {
        if (body.categoriaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione una categoría");
        }
        String descripcion = texto(body.descripcion(), "La descripción es obligatoria");
        CategoriaReporte categoria = categorias.findById(body.categoriaId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        if (!categoria.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría está desactivada");
        }

        Integrante yo = integranteActual(auth);
        Inmueble inmueble = inmuebleParaReporte(yo, body.inmuebleId());

        Reporte r = new Reporte();
        r.setCategoria(categoria);
        r.setCreador(yo);
        r.setInmueble(inmueble);
        r.setDescripcion(descripcion);
        reportes.save(r);
        guardarSeguimiento(r, auth.getName(), "Reporte creado");

        // La descripción no va a la auditoría: puede contener datos personales.
        auditoria.registrar("CREAR", "Reporte", r.getId(),
                "Categoría " + categoria.getNombre()
                        + (inmueble != null ? ", inmueble " + inmueble.getIdentificacion() : ""));
        return dto(r, false);
    }

    // RF-091: la imagen se adjunta después de crear el reporte, solo por quien lo creó.
    @PostMapping("/{id}/evidencia")
    @Transactional
    public ReporteDto adjuntarEvidencia(@PathVariable Long id,
                                        @RequestParam("archivo") MultipartFile archivo,
                                        Authentication auth) {
        Reporte r = buscar(id);
        Integrante yo = integranteActual(auth);
        if (!r.getCreador().getId().equals(yo.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien creó el reporte adjunta la evidencia");
        }
        if (r.getEvidenciaArchivo() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reporte ya tiene una imagen adjunta");
        }
        if (archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione una imagen");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen no puede pesar más de 5 MB");
        }

        try {
            byte[] bytes = archivo.getBytes();
            String tipo = detectarTipo(bytes);
            if (tipo == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Solo se aceptan imágenes JPG, PNG o WebP");
            }
            Path base = carpeta();
            Files.createDirectories(base);
            // El nombre lo genera el servidor: nunca se usa el del cliente.
            String nombre = r.getId() + "-" + UUID.randomUUID() + extension(tipo);
            Files.write(base.resolve(nombre), bytes);

            r.setEvidenciaArchivo(nombre);
            r.setEvidenciaTipo(tipo);
            reportes.save(r);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen");
        }

        auditoria.registrar("ADJUNTAR", "Reporte", r.getId(), "Imagen de evidencia");
        return dto(r, false);
    }

    // Ver la imagen: administrador, quien creó el reporte o un residente de su inmueble.
    @GetMapping("/{id}/evidencia")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> verEvidencia(@PathVariable Long id, Authentication auth) {
        Reporte r = buscar(id);
        if (!puedeVer(r, auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a ese reporte");
        }
        if (r.getEvidenciaArchivo() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El reporte no tiene imagen");
        }
        try {
            Path base = carpeta();
            Path archivo = base.resolve(r.getEvidenciaArchivo()).normalize();
            if (!archivo.startsWith(base) || !Files.exists(archivo)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la imagen");
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(r.getEvidenciaTipo()))
                    .header("X-Content-Type-Options", "nosniff")
                    .body(Files.readAllBytes(archivo));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer la imagen");
        }
    }

    // RF-092: estado de los reportes que creé.
    @GetMapping("/mis-reportes")
    @Transactional(readOnly = true)
    public List<ReporteDto> misReportes(Authentication auth) {
        Integrante yo = integranteActual(auth);
        return reportes.findByCreadorIdOrderByIdDesc(yo.getId()).stream()
                .map(r -> dto(r, false)).toList();
    }

    // RF-093: historial de reportes de mi inmueble (propietario residente y arrendatario).
    @GetMapping("/de-mi-inmueble")
    @Transactional(readOnly = true)
    public List<ReporteDto> deMiInmueble(Authentication auth) {
        Integrante yo = integranteActual(auth);
        if (yo.getRol() == RolIntegrante.VIGILANTE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El vigilante solo consulta los reportes que creó");
        }
        List<Long> ids = inmueblesResidente(yo).stream().map(Inmueble::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return reportes.findByInmuebleIdInOrderByIdDesc(ids).stream()
                .map(r -> dto(r, false)).toList();
    }

    // Detalle: el administrador lo ve completo (RF-095); los demás, sin observaciones internas.
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ReporteDto detalle(@PathVariable Long id, Authentication auth) {
        Reporte r = buscar(id);
        if (!puedeVer(r, auth)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a ese reporte");
        }
        return dto(r, esAdmin(auth));
    }

    // ---------- CU-16: administrador (todo lo siguiente cae en /api/**) ----------

    // RF-094
    @GetMapping
    @Transactional(readOnly = true)
    public List<ReporteDto> listar(@RequestParam(required = false) EstadoReporte estado,
                                   @RequestParam(required = false) Long categoriaId,
                                   @RequestParam(required = false) Long inmuebleId) {
        return reportes.findAllByOrderByIdDesc().stream()
                .filter(r -> estado == null || r.getEstado() == estado)
                .filter(r -> categoriaId == null || r.getCategoria().getId().equals(categoriaId))
                .filter(r -> inmuebleId == null
                        || (r.getInmueble() != null && r.getInmueble().getId().equals(inmuebleId)))
                .map(r -> dto(r, true))
                .toList();
    }

    // Candidatos a responsable (RF-096).
    @GetMapping("/admin/responsables")
    @Transactional(readOnly = true)
    public List<Opcion> responsables() {
        return integrantes.findAll().stream()
                .filter(Integrante::isActivo)
                .filter(i -> ROLES_RESPONSABLE.contains(i.getRol()))
                .sorted(Comparator.comparing(Integrante::getNombres))
                .map(i -> new Opcion(i.getId(), nombre(i) + " (" + i.getRol().name().toLowerCase() + ")"))
                .toList();
    }

    // RF-096
    @PatchMapping("/{id}/responsable")
    @Transactional
    public ReporteDto asignar(@PathVariable Long id, @RequestBody AsignacionResponsable body,
                              Authentication auth) {
        Reporte r = buscar(id);
        exigirNoCerrado(r);
        if (body.responsableId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el responsable");
        }
        Integrante responsable = integrantes.findById(body.responsableId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsable no encontrado"));
        if (!responsable.isActivo() || !ROLES_RESPONSABLE.contains(responsable.getRol())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El responsable debe ser un administrador o vigilante activo");
        }
        r.setResponsable(responsable);
        reportes.save(r);
        guardarSeguimiento(r, auth.getName(), "Responsable asignado: " + nombre(responsable));

        auditoria.registrar("ASIGNAR", "Reporte", r.getId(), "Responsable integrante " + responsable.getId());
        avisarReporte(r, "Se asignó un responsable al reporte.");
        return dto(r, true);
    }

    // RF-097: de Abierto a En proceso y viceversa. Cerrar se hace con RF-099.
    @PatchMapping("/{id}/estado")
    @Transactional
    public ReporteDto cambiarEstado(@PathVariable Long id, @RequestParam EstadoReporte estado,
                                    Authentication auth) {
        Reporte r = buscar(id);
        exigirNoCerrado(r);
        if (estado == EstadoReporte.CERRADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para cerrar un reporte use la opción de cerrar e indique la solución");
        }
        if (r.getEstado() == estado) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reporte ya está en ese estado");
        }
        EstadoReporte anterior = r.getEstado();
        r.setEstado(estado);
        reportes.save(r);
        guardarSeguimiento(r, auth.getName(), "Estado: " + anterior + " -> " + estado);

        auditoria.registrar("CAMBIAR_ESTADO", "Reporte", r.getId(), anterior + " -> " + estado);
        avisarReporte(r, "El reporte pasó de " + anterior + " a " + estado + ".");
        return dto(r, true);
    }

    // RF-098
    @PostMapping("/{id}/seguimiento")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReporteDto registrarSeguimiento(@PathVariable Long id, @RequestBody NuevaObservacion body,
                                           Authentication auth) {
        Reporte r = buscar(id);
        exigirNoCerrado(r);
        String observacion = texto(body.observacion(), "La observación es obligatoria");
        guardarSeguimiento(r, auth.getName(), observacion);

        auditoria.registrar("SEGUIMIENTO", "Reporte", r.getId(), null);
        return dto(r, true);
    }

    // RF-099
    @PatchMapping("/{id}/cerrar")
    @Transactional
    public ReporteDto cerrar(@PathVariable Long id, @RequestBody CierreReporte body, Authentication auth) {
        Reporte r = buscar(id);
        exigirNoCerrado(r);
        String solucion = texto(body.solucion(), "Debe indicar la solución aplicada");

        r.setEstado(EstadoReporte.CERRADO);
        r.setSolucion(solucion);
        r.setCerradoEn(LocalDateTime.now());
        reportes.save(r);
        guardarSeguimiento(r, auth.getName(), "Reporte cerrado. Solución: " + solucion);

        auditoria.registrar("CERRAR", "Reporte", r.getId(), null);
        avisarReporte(r, "El reporte fue cerrado. Solución: " + solucion);
        return dto(r, true);
    }

    // ---------- Ayudas ----------

    // CU-17: aviso a los residentes del inmueble (RN-29). Un fallo no afecta a la operación.
    private void avisarReporte(Reporte r, String texto) {
        if (r.getInmueble() == null) return;
        notificaciones.avisar(EventoNotificacion.REPORTE_ACTUALIZADO, r.getInmueble(),
                "Reporte N.° " + r.getId() + " actualizado", texto);
    }

    private Reporte buscar(Long id) {
        return reportes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));
    }

    private void exigirNoCerrado(Reporte r) {
        if (r.getEstado() == EstadoReporte.CERRADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reporte ya está cerrado");
        }
    }

    private String texto(String s, String mensajeVacio) {
        if (s == null || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeVacio);
        }
        String t = s.trim();
        if (t.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El texto no puede pasar de 1000 caracteres");
        }
        return t;
    }

    private void guardarSeguimiento(Reporte r, String usuario, String observacion) {
        SeguimientoReporte s = new SeguimientoReporte();
        s.setReporte(r);
        s.setUsuario(usuario);
        s.setEstado(r.getEstado());
        s.setObservacion(observacion);
        seguimientos.save(s);
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // Inmuebles con vinculación vigente como propietario residente o arrendatario.
    private List<Inmueble> inmueblesResidente(Integrante yo) {
        return vinculaciones.findByIntegranteIdAndEstado(yo.getId(), EstadoVinculacion.VIGENTE).stream()
                .filter(v -> TIPOS_RESIDENTE.contains(v.getTipoVinculo()))
                .map(Vinculacion::getInmueble)
                .filter(Inmueble::isActivo)
                .distinct()
                .toList();
    }

    // El inmueble es opcional. El residente solo puede elegir uno propio; el vigilante, cualquier activo.
    private Inmueble inmuebleParaReporte(Integrante yo, Long inmuebleId) {
        if (yo.getRol() == RolIntegrante.VIGILANTE) {
            if (inmuebleId == null) return null;
            Inmueble i = inmuebles.findById(inmuebleId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
            if (!i.isActivo()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble está desactivado");
            }
            return i;
        }
        List<Inmueble> propios = inmueblesResidente(yo);
        if (propios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Necesita una vinculación vigente como propietario residente o arrendatario");
        }
        if (inmuebleId == null) return null;
        return propios.stream().filter(i -> i.getId().equals(inmuebleId)).findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene vinculación vigente con ese inmueble"));
    }

    // Administrador, quien lo creó, o un residente vinculado al inmueble del reporte (RN-07).
    private boolean puedeVer(Reporte r, Authentication auth) {
        if (esAdmin(auth)) return true;
        Integrante yo = integranteActual(auth);
        if (r.getCreador().getId().equals(yo.getId())) return true;
        return r.getInmueble() != null
                && inmueblesResidente(yo).stream().anyMatch(i -> i.getId().equals(r.getInmueble().getId()));
    }

    private Path carpeta() {
        return Paths.get(uploadsDir, "reportes").toAbsolutePath().normalize();
    }

    // El tipo se decide por el contenido real del archivo, no por su nombre ni su extensión.
    private String detectarTipo(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String extension(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
    }

    private String nombre(Integrante i) {
        return i == null ? null : i.getNombres() + " " + i.getApellidos();
    }

    private ReporteDto dto(Reporte r, boolean completo) {
        Inmueble in = r.getInmueble();
        Integrante resp = r.getResponsable();
        List<SeguimientoDto> seg = completo
                ? seguimientos.findByReporteIdOrderByIdAsc(r.getId()).stream()
                        .map(s -> new SeguimientoDto(s.getId(), s.getFecha(), s.getUsuario(),
                                s.getEstado().name(), s.getObservacion()))
                        .toList()
                : null;
        return new ReporteDto(r.getId(), r.getCategoria().getId(), r.getCategoria().getNombre(),
                r.getDescripcion(), r.getEstado().name(),
                in == null ? null : in.getId(), in == null ? null : in.getIdentificacion(),
                nombre(r.getCreador()), r.getCreadoEn(),
                r.getEvidenciaArchivo() != null, r.getSolucion(), r.getCerradoEn(),
                completo && resp != null ? resp.getId() : null,
                completo ? nombre(resp) : null,
                seg);
    }
}