package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.CesionUsoRepository;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.TipoInmuebleRepository;
import com.proyecto.plataforma_ph.repository.TitularidadParqueaderoRepository;
import com.proyecto.plataforma_ph.repository.VehiculoRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-09: parqueaderos y cesiones de uso (RF-056 a RF-062) y RF-063 para el vigilante.
 * Un parqueadero es un inmueble cuyo tipo se llama "Parqueadero..." (convención del proyecto).
 * Las rutas /api/parqueaderos y /api/cesiones caen en /api/** (solo administrador);
 * /api/porteria/** es del vigilante.
 */
@RestController
@RequestMapping("/api")
public class ParqueaderoController {

    public record NuevoParqueadero(String identificacion, Naturaleza naturaleza, Long tipoInmuebleId,
                                   Long contenedorId, BigDecimal coeficiente, BigDecimal area) {}

    public record CambioParqueadero(String identificacion, Long contenedorId) {}

    public record AsignacionTitular(Long titularId) {}

    public record NuevaCesion(Long inmuebleUsuarioId, LocalDate fechaInicio, LocalDate fechaFin,
                              Long vehiculoId, Boolean autorizadaAsamblea) {}

    public record ParqueaderoDto(Long id, String identificacion, String tipo, Naturaleza naturaleza,
                                 Long contenedorId, String contenedor, boolean activo,
                                 Long titularId, String titular,
                                 Long usoVigenteId, String usoVigente, boolean enCesion) {}

    public record CesionDto(Long id, Long parqueaderoId, String parqueadero,
                            Long titularId, String titular,
                            Long inmuebleUsuarioId, String inmuebleUsuario,
                            Long vehiculoId, String placa,
                            LocalDate fechaInicio, LocalDate fechaFin,
                            String estado, boolean autorizadaAsamblea) {}

    public record UsoVigenteDto(Long parqueaderoId, String parqueadero, String titular,
                                String usoVigente, boolean porCesion, LocalDate cesionHasta) {}

    private final InmuebleRepository inmuebles;
    private final TipoInmuebleRepository tipos;
    private final TitularidadParqueaderoRepository titularidades;
    private final CesionUsoRepository cesiones;
    private final VehiculoRepository vehiculos;
    private final AuditoriaService auditoria;

    public ParqueaderoController(InmuebleRepository inmuebles, TipoInmuebleRepository tipos,
                                 TitularidadParqueaderoRepository titularidades,
                                 CesionUsoRepository cesiones, VehiculoRepository vehiculos,
                                 AuditoriaService auditoria) {
        this.inmuebles = inmuebles;
        this.tipos = tipos;
        this.titularidades = titularidades;
        this.cesiones = cesiones;
        this.vehiculos = vehiculos;
        this.auditoria = auditoria;
    }

    // ---------- Administrador ----------

    // RF-058: listado de parqueaderos con titular y uso vigente.
    @GetMapping("/parqueaderos")
    @Transactional(readOnly = true)
    public List<ParqueaderoDto> listar(@RequestParam(required = false) Boolean activo) {
        return construirLista().stream()
                .filter(p -> activo == null || p.activo() == activo)
                .toList();
    }

    // RF-056
    @PostMapping("/parqueaderos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ParqueaderoDto registrar(@RequestBody NuevoParqueadero body) {
        if (body.identificacion() == null || body.identificacion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La identificación es obligatoria");
        }
        if (body.naturaleza() == null || body.tipoInmuebleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la naturaleza y el tipo de inmueble");
        }
        TipoInmueble tipo = tipos.findById(body.tipoInmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de inmueble no encontrado"));
        if (!tipo.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El tipo de inmueble está desactivado");
        }
        if (!norm(tipo.getNombre()).startsWith("parqueadero")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo debe ser de parqueadero (su nombre empieza por \"Parqueadero\")");
        }
        if (norm(tipo.getNombre()).contains("visitante") && body.naturaleza() != Naturaleza.BIEN_COMUN_USO_GENERAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los parqueaderos de visitantes son bienes comunes de uso general");
        }
        String identificacion = body.identificacion().trim();
        validarIdentificacionLibre(identificacion, null);

        Inmueble p = new Inmueble();
        p.setIdentificacion(identificacion);
        p.setNaturaleza(body.naturaleza());
        p.setTipoInmueble(tipo);
        p.setContenedor(resolverContenedor(body.contenedorId(), null));
        p.setCoeficiente(body.coeficiente());
        p.setArea(body.area());
        p.setActivo(true);
        inmuebles.save(p);

        auditoria.registrar("CREAR", "Inmueble", p.getId(), "Parqueadero " + identificacion);
        return aDto(p, null, null);
    }

    // RF-059: identificación y contenedor.
    @PutMapping("/parqueaderos/{id}")
    @Transactional
    public ParqueaderoDto modificar(@PathVariable Long id, @RequestBody CambioParqueadero body) {
        Inmueble p = buscarParqueadero(id);
        if (body.identificacion() == null || body.identificacion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La identificación es obligatoria");
        }
        String identificacion = body.identificacion().trim();
        validarIdentificacionLibre(identificacion, id);

        p.setIdentificacion(identificacion);
        p.setContenedor(resolverContenedor(body.contenedorId(), id));
        inmuebles.save(p);

        auditoria.registrar("MODIFICAR", "Inmueble", p.getId(), "Parqueadero " + identificacion);
        return dtoDe(p);
    }

    // RF-057: asignar el parqueadero a un inmueble titular.
    @PutMapping("/parqueaderos/{id}/titular")
    @Transactional
    public ParqueaderoDto asignarTitular(@PathVariable Long id, @RequestBody AsignacionTitular body) {
        Inmueble p = buscarParqueadero(id);
        if (!p.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parqueadero está desactivado");
        }
        if (p.getNaturaleza() == Naturaleza.BIEN_COMUN_USO_GENERAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bien común de uso general no se asigna a ningún inmueble");
        }
        if (body.titularId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el inmueble titular");
        }
        Inmueble titular = inmuebles.findById(body.titularId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble titular no encontrado"));
        if (!titular.isActivo() || esParqueadero(titular)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El titular debe ser un inmueble activo que no sea un parqueadero");
        }

        var actual = titularidades.findByParqueaderoIdAndFechaFinIsNull(id);
        if (actual.isPresent() && actual.get().getTitular().getId().equals(titular.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese inmueble ya es el titular");
        }
        if (!cesiones.findByParqueaderoIdAndEstado(id, EstadoCesion.VIGENTE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finalice las cesiones vigentes antes de cambiar el titular");
        }
        actual.ifPresent(t -> {
            t.setFechaFin(LocalDate.now());
            titularidades.save(t);
        });

        TitularidadParqueadero nueva = new TitularidadParqueadero();
        nueva.setParqueadero(p);
        nueva.setTitular(titular);
        nueva.setFechaInicio(LocalDate.now());
        titularidades.save(nueva);

        auditoria.registrar("ASIGNAR", "Parqueadero", p.getId(),
                p.getIdentificacion() + " -> titular " + titular.getIdentificacion());
        return dtoDe(p);
    }

    // RF-060: registrar una cesión de uso.
    @PostMapping("/parqueaderos/{id}/cesiones")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CesionDto registrarCesion(@PathVariable Long id, @RequestBody NuevaCesion body) {
        Inmueble p = buscarParqueadero(id);
        if (!p.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parqueadero está desactivado");
        }
        // RN-17
        if (p.getNaturaleza() == Naturaleza.BIEN_COMUN_USO_GENERAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bien común de uso general no admite cesión de uso exclusivo");
        }
        if (!p.getTipoInmueble().isPermiteCesion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El tipo de inmueble no permite cesión de uso");
        }
        // RN-35
        if (p.getNaturaleza() == Naturaleza.BIEN_COMUN_USO_EXCLUSIVO
                && !Boolean.TRUE.equals(body.autorizadaAsamblea())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un bien común de uso exclusivo solo se cede con autorización de la asamblea "
                            + "(indique autorizadaAsamblea=true). La cesión nunca equivale a vender el derecho.");
        }

        var titularidad = titularidades.findByParqueaderoIdAndFechaFinIsNull(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Primero asigne un titular al parqueadero"));
        Inmueble titular = titularidad.getTitular();

        if (body.inmuebleUsuarioId() == null || body.fechaInicio() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique el inmueble usuario y la fecha de inicio");
        }
        Inmueble usuario = inmuebles.findById(body.inmuebleUsuarioId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble usuario no encontrado"));
        if (!usuario.isActivo() || esParqueadero(usuario)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El inmueble usuario debe estar activo y no ser un parqueadero");
        }
        if (usuario.getId().equals(titular.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El titular no puede cederse el parqueadero a sí mismo");
        }

        LocalDate inicio = body.fechaInicio();
        LocalDate fin = body.fechaFin();
        if (fin != null && fin.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede ser anterior a la de inicio");
        }
        if (fin != null && fin.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin no puede estar en el pasado");
        }

        // RN-18: no puede haber dos cesiones vigentes y superpuestas.
        LocalDate finNueva = fin != null ? fin : LocalDate.MAX;
        for (CesionUso c : cesiones.findByParqueaderoIdAndEstado(id, EstadoCesion.VIGENTE)) {
            LocalDate finExistente = c.getFechaFin() != null ? c.getFechaFin() : LocalDate.MAX;
            if (!inicio.isAfter(finExistente) && !finNueva.isBefore(c.getFechaInicio())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe una cesión vigente que se superpone con ese periodo");
            }
        }

        Vehiculo vehiculo = null;
        if (body.vehiculoId() != null) {
            vehiculo = vehiculos.findById(body.vehiculoId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehículo no encontrado"));
            if (!vehiculo.isActivo() || !vehiculo.getInmueble().getId().equals(usuario.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El vehículo debe estar activo y pertenecer al inmueble usuario");
            }
        }

        CesionUso c = new CesionUso();
        c.setParqueadero(p);
        c.setInmuebleTitular(titular);
        c.setInmuebleUsuario(usuario);
        c.setVehiculo(vehiculo);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        c.setAutorizadaAsamblea(Boolean.TRUE.equals(body.autorizadaAsamblea()));
        cesiones.save(c);

        auditoria.registrar("CREAR", "CesionUso", c.getId(),
                p.getIdentificacion() + ": " + titular.getIdentificacion() + " -> " + usuario.getIdentificacion());
        return aDto(c);
    }

    // RF-061: finalizar una cesión vigente indicando la fecha de terminación.
    @PatchMapping("/cesiones/{id}/finalizar")
    @Transactional
    public CesionDto finalizarCesion(@PathVariable Long id,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaTerminacion) {
        CesionUso c = cesiones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cesión no encontrada"));
        if (c.getEstado() != EstadoCesion.VIGENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cesión ya está finalizada");
        }
        if (fechaTerminacion.isBefore(c.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de terminación no puede ser anterior al inicio");
        }
        if (fechaTerminacion.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de terminación no puede ser futura");
        }
        c.setFechaFin(fechaTerminacion);
        c.setEstado(EstadoCesion.FINALIZADA);
        cesiones.save(c);

        auditoria.registrar("FINALIZAR", "CesionUso", c.getId(), "Terminación: " + fechaTerminacion);
        return aDto(c);
    }

    // RF-062: cesiones vigentes y finalizadas de un parqueadero (?estado=VIGENTE|FINALIZADA).
    @GetMapping("/parqueaderos/{id}/cesiones")
    @Transactional(readOnly = true)
    public List<CesionDto> consultarCesiones(@PathVariable Long id,
                                             @RequestParam(required = false) EstadoCesion estado) {
        buscarParqueadero(id);
        return cesiones.findByParqueaderoIdOrderByIdDesc(id).stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .map(this::aDto)
                .toList();
    }

    // ---------- Vigilante (RF-063) ----------

    // Parqueaderos activos con su uso vigente, para elegir uno en portería.
    @GetMapping("/porteria/parqueaderos")
    @Transactional(readOnly = true)
    public List<ParqueaderoDto> listarParaPorteria() {
        return construirLista().stream().filter(ParqueaderoDto::activo).toList();
    }

    // RF-063: qué inmueble tiene el uso vigente de un parqueadero.
    @GetMapping("/porteria/parqueaderos/{id}/uso-vigente")
    @Transactional(readOnly = true)
    public UsoVigenteDto usoVigente(@PathVariable Long id) {
        Inmueble p = buscarParqueadero(id);
        LocalDate hoy = LocalDate.now();

        Inmueble titular = titularidades.findByParqueaderoIdAndFechaFinIsNull(id)
                .map(TitularidadParqueadero::getTitular).orElse(null);
        CesionUso enCurso = cesiones.findByParqueaderoIdAndEstado(id, EstadoCesion.VIGENTE).stream()
                .filter(c -> c.enCurso(hoy)).findFirst().orElse(null);

        Inmueble uso = enCurso != null ? enCurso.getInmuebleUsuario() : titular;
        return new UsoVigenteDto(p.getId(), p.getIdentificacion(),
                titular == null ? null : titular.getIdentificacion(),
                uso == null ? null : uso.getIdentificacion(),
                enCurso != null,
                enCurso == null ? null : enCurso.getFechaFin());
    }

    // ---------- Ayudas ----------

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private boolean esParqueadero(Inmueble i) {
        return i.getTipoInmueble() != null && norm(i.getTipoInmueble().getNombre()).startsWith("parqueadero");
    }

    private Inmueble buscarParqueadero(Long id) {
        Inmueble i = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Parqueadero no encontrado"));
        if (!esParqueadero(i)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parqueadero no encontrado");
        }
        return i;
    }

    private void validarIdentificacionLibre(String identificacion, Long idPropio) {
        boolean repetida = inmuebles.findAll().stream().anyMatch(i ->
                i.getIdentificacion().equalsIgnoreCase(identificacion)
                        && (idPropio == null || !i.getId().equals(idPropio)));
        if (repetida) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un inmueble con la identificación " + identificacion);
        }
    }

    private Inmueble resolverContenedor(Long contenedorId, Long idPropio) {
        if (contenedorId == null) return null;
        if (contenedorId.equals(idPropio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un inmueble no puede contenerse a sí mismo");
        }
        Inmueble c = inmuebles.findById(contenedorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble contenedor no encontrado"));
        if (!c.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble contenedor está desactivado");
        }
        return c;
    }

    // Titular vigente y cesión en curso de todos los parqueaderos, sin una consulta por fila.
    private List<ParqueaderoDto> construirLista() {
        LocalDate hoy = LocalDate.now();
        Map<Long, Inmueble> titulares = new HashMap<>();
        for (TitularidadParqueadero t : titularidades.findByFechaFinIsNull()) {
            titulares.put(t.getParqueadero().getId(), t.getTitular());
        }
        Map<Long, Inmueble> usuarios = new HashMap<>();
        for (CesionUso c : cesiones.findByEstado(EstadoCesion.VIGENTE)) {
            if (c.enCurso(hoy)) {
                usuarios.put(c.getParqueadero().getId(), c.getInmuebleUsuario());
            }
        }
        return inmuebles.findAll().stream()
                .filter(this::esParqueadero)
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .map(p -> aDto(p, titulares.get(p.getId()), usuarios.get(p.getId())))
                .toList();
    }

    private ParqueaderoDto dtoDe(Inmueble p) {
        Inmueble titular = titularidades.findByParqueaderoIdAndFechaFinIsNull(p.getId())
                .map(TitularidadParqueadero::getTitular).orElse(null);
        LocalDate hoy = LocalDate.now();
        Inmueble usuario = cesiones.findByParqueaderoIdAndEstado(p.getId(), EstadoCesion.VIGENTE).stream()
                .filter(c -> c.enCurso(hoy)).map(CesionUso::getInmuebleUsuario).findFirst().orElse(null);
        return aDto(p, titular, usuario);
    }

    // RN-19: el uso vigente es quien tiene la cesión en curso; si no hay, el titular.
    private ParqueaderoDto aDto(Inmueble p, Inmueble titular, Inmueble usuario) {
        Inmueble uso = usuario != null ? usuario : titular;
        Inmueble cont = p.getContenedor();
        return new ParqueaderoDto(p.getId(), p.getIdentificacion(), p.getTipoInmueble().getNombre(),
                p.getNaturaleza(),
                cont == null ? null : cont.getId(), cont == null ? null : cont.getIdentificacion(),
                p.isActivo(),
                titular == null ? null : titular.getId(), titular == null ? null : titular.getIdentificacion(),
                uso == null ? null : uso.getId(), uso == null ? null : uso.getIdentificacion(),
                usuario != null);
    }

    private CesionDto aDto(CesionUso c) {
        Vehiculo v = c.getVehiculo();
        return new CesionDto(c.getId(), c.getParqueadero().getId(), c.getParqueadero().getIdentificacion(),
                c.getInmuebleTitular().getId(), c.getInmuebleTitular().getIdentificacion(),
                c.getInmuebleUsuario().getId(), c.getInmuebleUsuario().getIdentificacion(),
                v == null ? null : v.getId(), v == null ? null : v.getPlaca(),
                c.getFechaInicio(), c.getFechaFin(), c.getEstado().name(), c.isAutorizadaAsamblea());
    }
}