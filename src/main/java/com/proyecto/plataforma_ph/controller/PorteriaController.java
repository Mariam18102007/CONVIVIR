package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.AutorizacionRepository;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.MovimientoAccesoRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// CU-07: portería. El vigilante consulta autorizaciones y registra ingresos y salidas.
// RN-13: el vigilante solo escribe movimientos; no modifica vinculaciones ni autorizaciones.
@RestController
@RequestMapping("/api")
public class PorteriaController {

    public record IngresoRequest(Long autorizacionId, String placa) {}

    public record SinAutorizacionRequest(Long inmuebleId, String nombre, String documento,
                                         String motivo, String placa) {}

    public record AutorizacionPorteriaDto(Long id, String tipo, String nombre, String documento,
                                          Long inmuebleId, String inmueble,
                                          LocalDate fechaInicio, LocalDate fechaFin,
                                          List<String> dias, LocalTime horaInicio, LocalTime horaFin,
                                          boolean usaParqueadero, boolean puedeIngresar,
                                          String motivoRechazo) {}

    public record AccesoDto(Long id, String tipo, String nombre, String documento,
                            Long inmuebleId, String inmueble, String placa, String motivo,
                            LocalDateTime ingresoEn, LocalDateTime salidaEn,
                            String vigilanteIngreso, String vigilanteSalida, boolean dentro) {}

    private final AutorizacionRepository autorizaciones;
    private final MovimientoAccesoRepository movimientos;
    private final InmuebleRepository inmuebles;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    public PorteriaController(AutorizacionRepository autorizaciones,
                              MovimientoAccesoRepository movimientos,
                              InmuebleRepository inmuebles,
                              UsuarioRepository usuarios,
                              AuditoriaService auditoria,
                              NotificacionService notificaciones) {
        this.autorizaciones = autorizaciones;
        this.movimientos = movimientos;
        this.inmuebles = inmuebles;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
    }

    // RF-031, RF-032 y RF-043: buscar autorizaciones vigentes por documento o por inmueble.
    // Cada resultado indica si la persona puede ingresar AHORA (vigencia, día y franja horaria).
    @GetMapping("/porteria/autorizaciones")
    @Transactional(readOnly = true)
    public List<AutorizacionPorteriaDto> buscar(@RequestParam(required = false) String documento,
                                                @RequestParam(required = false) Long inmuebleId) {
        List<Autorizacion> lista;
        if (documento != null && !documento.isBlank()) {
            lista = autorizaciones.findByDocumentoOrderByIdDesc(documento.trim());
        } else if (inmuebleId != null) {
            lista = autorizaciones.findByInmuebleIdOrderByIdDesc(inmuebleId);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique documento o inmuebleId");
        }
        LocalDateTime ahora = LocalDateTime.now();
        return lista.stream()
                .filter(a -> a.estadoEfectivo() == EstadoAutorizacion.VIGENTE)
                .map(a -> aDtoPorteria(a, ahora))
                .toList();
    }

    // RF-033 y RF-044: ingreso de un visitante o autorizado recurrente con autorización vigente.
    @PostMapping("/porteria/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AccesoDto ingreso(@RequestBody IngresoRequest req, Authentication auth) {
        if (req.autorizacionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique autorizacionId");
        }
        Autorizacion a = autorizaciones.findById(req.autorizacionId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Autorización no encontrada"));

        LocalDateTime ahora = LocalDateTime.now();
        String rechazo = rechazo(a, ahora);
        if (rechazo != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rechazo);
        }

        MovimientoAcceso m = new MovimientoAcceso();
        m.setAutorizacion(a);
        m.setInmueble(a.getInmueble());
        m.setNombre(a.getNombre());
        m.setDocumento(a.getDocumento());
        m.setPlaca(normalizarPlaca(req.placa()));
        m.setIngresoEn(ahora);
        m.setVigilanteIngreso(vigilanteActual(auth));
        m = movimientos.save(m);

        // La visita ocasional se gasta con el primer ingreso; la recurrente sigue vigente.
        if (a.getTipo() == TipoAutorizacion.OCASIONAL) {
            a.setEstado(EstadoAutorizacion.UTILIZADA);
            autorizaciones.save(a);
        }

        auditoria.registrar("INGRESAR", "Acceso", m.getId(),
                "Inmueble " + a.getInmueble().getIdentificacion());

        // CU-17: aviso a los residentes del inmueble (RN-29). Un fallo no afecta al ingreso.
        boolean ocasional = a.getTipo() == TipoAutorizacion.OCASIONAL;
        notificaciones.avisar(
                ocasional ? EventoNotificacion.VISITANTE_INGRESO : EventoNotificacion.AUTORIZACION_RECURRENTE,
                a.getInmueble(),
                ocasional ? "Ingresó su visitante" : "Ingresó un autorizado recurrente",
                a.getNombre() + " ingresó al conjunto con destino al inmueble "
                        + a.getInmueble().getIdentificacion() + ".");
        return aDtoAcceso(m);
    }

    // RF-035 y RN-12: ingreso sin autorización previa, solo con motivo documentado.
    @PostMapping("/porteria/ingresos/sin-autorizacion")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AccesoDto ingresoSinAutorizacion(@RequestBody SinAutorizacionRequest req, Authentication auth) {
        if (req.inmuebleId() == null || vacio(req.nombre()) || vacio(req.documento())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar inmueble de destino, nombre y documento");
        }
        if (vacio(req.motivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un ingreso sin autorización previa exige un motivo");
        }
        Inmueble inmueble = inmuebles.findById(req.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        if (!inmueble.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble está desactivado");
        }

        MovimientoAcceso m = new MovimientoAcceso();
        m.setInmueble(inmueble);
        m.setNombre(req.nombre().trim());
        m.setDocumento(req.documento().trim());
        m.setMotivo(req.motivo().trim());
        m.setPlaca(normalizarPlaca(req.placa()));
        m.setIngresoEn(LocalDateTime.now());
        m.setVigilanteIngreso(vigilanteActual(auth));
        m = movimientos.save(m);

        auditoria.registrar("INGRESAR_SIN_AUTORIZACION", "Acceso", m.getId(),
                "Inmueble " + inmueble.getIdentificacion() + ". Motivo: " + m.getMotivo());
        return aDtoAcceso(m);
    }

    // RF-034 y RF-044: salida.
    @PatchMapping("/porteria/ingresos/{id}/salida")
    @Transactional
    public AccesoDto salida(@PathVariable Long id, Authentication auth) {
        MovimientoAcceso m = movimientos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        if (m.getSalidaEn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa persona ya registró su salida");
        }
        m.setSalidaEn(LocalDateTime.now());
        m.setVigilanteSalida(vigilanteActual(auth));
        movimientos.save(m);

        auditoria.registrar("SALIR", "Acceso", m.getId(),
                "Inmueble " + m.getInmueble().getIdentificacion());
        return aDtoAcceso(m);
    }

    // RF-036: quiénes están dentro del conjunto ahora.
    @GetMapping("/porteria/dentro")
    @Transactional(readOnly = true)
    public List<AccesoDto> dentro() {
        return movimientos.findBySalidaEnIsNullOrderByIngresoEnDesc().stream()
                .map(this::aDtoAcceso).toList();
    }

    // RF-037: historial para el administrador, por inmueble y periodo (fechas AAAA-MM-DD).
    @GetMapping("/accesos/historial")
    @Transactional(readOnly = true)
    public List<AccesoDto> historial(
            @RequestParam(required = false) Long inmuebleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        LocalDateTime d = inicio.atStartOfDay();
        LocalDateTime h = fin.atTime(LocalTime.MAX);

        List<MovimientoAcceso> lista = inmuebleId != null
                ? movimientos.findByInmuebleIdAndIngresoEnBetweenOrderByIngresoEnDesc(inmuebleId, d, h)
                : movimientos.findByIngresoEnBetweenOrderByIngresoEnDesc(d, h);
        return lista.stream().map(this::aDtoAcceso).toList();
    }

    // ---------- Reglas ----------

    // Devuelve null si puede ingresar ahora, o el motivo por el que no.
    private String rechazo(Autorizacion a, LocalDateTime ahora) {
        EstadoAutorizacion e = a.estadoEfectivo();
        if (e != EstadoAutorizacion.VIGENTE) {
            return "La autorización está " + e.name().toLowerCase() + ".";
        }
        if (!a.getInmueble().isActivo()) {
            return "El inmueble está desactivado.";
        }
        if (ahora.toLocalDate().isBefore(a.getFechaInicio())) {
            return "Todavía no empieza su vigencia (" + a.getFechaInicio() + ").";
        }
        if (a.getTipo() == TipoAutorizacion.RECURRENTE) {
            List<String> dias = a.getDiasSemana() == null ? List.of()
                    : Arrays.asList(a.getDiasSemana().split(","));
            if (!dias.contains(nombreDia(ahora.getDayOfWeek()))) {
                return "Hoy no es un día permitido (" + String.join(", ", dias) + ").";
            }
            LocalTime hora = ahora.toLocalTime();
            if (hora.isBefore(a.getHoraInicio()) || hora.isAfter(a.getHoraFin())) {
                return "Fuera de la franja horaria permitida (" + a.getHoraInicio()
                        + " a " + a.getHoraFin() + ").";
            }
        }
        if (movimientos.existsByAutorizacionIdAndSalidaEnIsNull(a.getId())) {
            return "Esta persona ya se encuentra dentro del conjunto.";
        }
        return null;
    }

    private String nombreDia(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private String normalizarPlaca(String placa) {
        if (vacio(placa)) {
            return null;
        }
        String p = placa.trim().toUpperCase();
        if (p.length() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La placa es demasiado larga");
        }
        return p;
    }

    private Integrante vigilanteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // ---------- DTOs ----------

    private AutorizacionPorteriaDto aDtoPorteria(Autorizacion a, LocalDateTime ahora) {
        String rechazo = rechazo(a, ahora);
        List<String> dias = a.getDiasSemana() == null || a.getDiasSemana().isBlank()
                ? List.of() : Arrays.asList(a.getDiasSemana().split(","));
        return new AutorizacionPorteriaDto(a.getId(), a.getTipo().name(), a.getNombre(),
                a.getDocumento(), a.getInmueble().getId(), a.getInmueble().getIdentificacion(),
                a.getFechaInicio(), a.getFechaFin(), dias, a.getHoraInicio(), a.getHoraFin(),
                a.isUsaParqueaderoVisitantes(), rechazo == null, rechazo);
    }

    private AccesoDto aDtoAcceso(MovimientoAcceso m) {
        String tipo = m.getAutorizacion() == null ? "SIN_AUTORIZACION" : m.getAutorizacion().getTipo().name();
        return new AccesoDto(m.getId(), tipo, m.getNombre(), m.getDocumento(),
                m.getInmueble().getId(), m.getInmueble().getIdentificacion(), m.getPlaca(),
                m.getMotivo(), m.getIngresoEn(), m.getSalidaEn(),
                nombreCompleto(m.getVigilanteIngreso()), nombreCompleto(m.getVigilanteSalida()),
                m.getSalidaEn() == null);
    }

    private String nombreCompleto(Integrante i) {
        return i == null ? null : i.getNombres() + " " + i.getApellidos();
    }
}