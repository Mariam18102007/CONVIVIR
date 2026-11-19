package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.ConfiguracionZona;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoReserva;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Reserva;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.UnidadCobro;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.ConfiguracionZonaRepository;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.ReservaRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-20 (residentes: consultar disponibilidad, solicitar, consultar y cancelar reservas) y
 * CU-21 (administrador: confirmar, rechazar, finalizar y multar), más la configuración de
 * tarifa y horario de cada zona común. La seguridad por rol está en SecurityConfig.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private static final long MAX_VALOR = 1_000_000_000_000L;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // RN-43: vínculos que cuentan como residente.
    private static final Set<TipoVinculo> RESIDENTES = Set.of(
            TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.ARRENDATARIO, TipoVinculo.RESIDENTE_AUTORIZADO);

    public record ConfigZona(UnidadCobro unidadCobro, Long tarifa, String horaApertura, String horaCierre) {}

    public record ZonaDto(Long id, String nombre, String tipo, boolean configurada, String unidadCobro,
                          String unidadEtiqueta, long tarifa, String horaApertura, String horaCierre) {}

    public record InmuebleOpcion(Long id, String identificacion, String vinculo) {}

    public record NuevaReserva(Long zonaId, Long inmuebleId, LocalDateTime inicio, LocalDateTime fin,
                               String finalidad) {}

    public record Franja(LocalDateTime inicio, LocalDateTime fin) {}

    public record Disponibilidad(boolean disponible, String motivo, long valor, long unidades, long tarifa,
                                 String unidad, List<Franja> ocupadas) {}

    public record NuevaMulta(Long inmuebleId, Long valor, String motivo) {}

    public record ReservaDto(Long id, Long zonaId, String zona, String solicitante, Long inmuebleId,
                             String inmueble, LocalDateTime inicio, LocalDateTime fin, String finalidad,
                             String estado, long valor, Long obligacionId, String motivo, boolean cancelable,
                             Long multaId, long multaValor, String motivoMulta, LocalDate fechaFinalizacion) {}

    private record Evaluacion(String motivo, boolean conflicto, long valor, long unidades, long tarifa,
                              String unidad, List<Franja> ocupadas) {}

    private final ReservaRepository reservas;
    private final ConfiguracionZonaRepository configuraciones;
    private final InmuebleRepository inmuebles;
    private final ObligacionRepository obligaciones;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public ReservaController(ReservaRepository reservas, ConfiguracionZonaRepository configuraciones,
                             InmuebleRepository inmuebles, ObligacionRepository obligaciones,
                             VinculacionRepository vinculaciones, UsuarioRepository usuarios,
                             AuditoriaService auditoria) {
        this.reservas = reservas;
        this.configuraciones = configuraciones;
        this.inmuebles = inmuebles;
        this.obligaciones = obligaciones;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // CU-20: residentes
    // =====================================================================

    // Zonas que se pueden reservar (con tarifa y horario configurados).
    @GetMapping("/zonas")
    @Transactional(readOnly = true)
    public List<ZonaDto> zonas() {
        List<ZonaDto> salida = new ArrayList<>();
        for (Inmueble z : zonasReservables()) {
            configuraciones.findByZonaId(z.getId()).ifPresent(c -> salida.add(zonaDto(z, c)));
        }
        return salida;
    }

    // Inmuebles a los que el residente puede imputar el alquiler (RF-116, RN-43).
    @GetMapping("/mis-inmuebles")
    @Transactional(readOnly = true)
    public List<InmuebleOpcion> misInmuebles(Authentication auth) {
        return new ArrayList<>(residencias(integranteActual(auth)).values());
    }

    // RF-115: disponibilidad de una zona para un periodo.
    @GetMapping("/disponibilidad")
    @Transactional(readOnly = true)
    public Disponibilidad disponibilidad(
            @RequestParam Long zonaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {
        Inmueble zona = zonaReservable(zonaId);
        ConfiguracionZona cfg = configuracion(zona);
        Evaluacion e = evaluar(zona, cfg, inicio, fin);
        return new Disponibilidad(e.motivo() == null, e.motivo(), e.valor(), e.unidades(), e.tarifa(),
                e.unidad(), e.ocupadas());
    }

    // RF-116: solicitar la reserva.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReservaDto solicitar(@RequestBody NuevaReserva body, Authentication auth) {
        Integrante yo = integranteActual(auth);
        Map<Long, InmuebleOpcion> residencias = residencias(yo);
        if (residencias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Para reservar necesita una vinculación residente vigente "
                            + "(propietario residente, arrendatario o residente autorizado)");
        }
        if (body.inmuebleId() == null || !residencias.containsKey(body.inmuebleId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Elija un inmueble en el que usted sea residente vigente");
        }
        if (body.inicio() == null || body.fin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el inicio y el fin de la reserva");
        }
        if (body.finalidad() == null || body.finalidad().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique la finalidad de la reserva");
        }
        String finalidad = body.finalidad().trim();
        if (finalidad.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La finalidad no puede pasar de 200 caracteres");
        }

        Inmueble zona = zonaReservable(body.zonaId());
        ConfiguracionZona cfg = configuracion(zona);
        Evaluacion e = evaluar(zona, cfg, body.inicio(), body.fin());
        if (e.motivo() != null) {
            throw new ResponseStatusException(e.conflicto() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, e.motivo());
        }

        Reserva r = new Reserva();
        r.setZona(zona);
        r.setSolicitante(yo);
        r.setInmueble(inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado")));
        r.setInicio(body.inicio());
        r.setFin(body.fin());
        r.setFinalidad(finalidad);
        r.setValor(e.valor());
        r.setEstado(EstadoReserva.SOLICITADA);
        reservas.save(r);

        auditoria.registrar("SOLICITAR_RESERVA", "Reserva", r.getId(),
                "Zona " + zona.getIdentificacion() + ", valor " + r.getValor());
        return dto(r, false);
    }

    // RF-117: mis reservas.
    @GetMapping("/mias")
    @Transactional(readOnly = true)
    public List<ReservaDto> mias(Authentication auth) {
        Integrante yo = integranteActual(auth);
        return reservas.findBySolicitanteIdOrderByIdDesc(yo.getId()).stream()
                .map(r -> dto(r, false))
                .toList();
    }

    // RF-118: cancelar una reserva propia mientras sea cancelable.
    @PatchMapping("/{id}/cancelar")
    @Transactional
    public ReservaDto cancelar(@PathVariable Long id, @RequestParam String motivo, Authentication auth) {
        Integrante yo = integranteActual(auth);
        Reserva r = buscar(id);
        if (!r.getSolicitante().getId().equals(yo.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa reserva no es suya");
        }
        String m = motivoValido(motivo, "la cancelación");
        if (r.getEstado() != EstadoReserva.SOLICITADA && r.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa reserva ya no se puede cancelar");
        }
        if (!r.getInicio().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva ya comenzó o pasó; no se puede cancelar");
        }
        Obligacion o = r.getObligacion();
        if (o != null) {
            if (o.getEstado() != EstadoObligacion.PENDIENTE || o.getSaldo() != o.getValor()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El alquiler ya tiene pagos registrados. Comuníquese con la administración");
            }
            // RN-27: la anulación queda con motivo y en auditoría.
            o.setEstado(EstadoObligacion.ANULADA);
            o.setMotivoAnulacion("Reserva #" + r.getId() + " cancelada: " + m);
            o.setAnuladaEn(LocalDateTime.now());
            obligaciones.save(o);
        }
        r.setEstado(EstadoReserva.CANCELADA);
        r.setMotivoCierre(m);
        r.setCerradaEn(LocalDateTime.now());
        reservas.save(r);

        auditoria.registrar("CANCELAR_RESERVA", "Reserva", r.getId(), "Motivo: " + m);
        return dto(r, false);
    }

    // =====================================================================
    // CU-21: administrador (cae en /api/**)
    // =====================================================================

    @GetMapping
    @Transactional(readOnly = true)
    public List<ReservaDto> listar(@RequestParam(required = false) EstadoReserva estado,
                                   @RequestParam(required = false) Long zonaId) {
        return reservas.findAllByOrderByIdDesc().stream()
                .filter(r -> estado == null || r.getEstado() == estado)
                .filter(r -> zonaId == null || r.getZona().getId().equals(zonaId))
                .map(r -> dto(r, true))
                .toList();
    }

    // RF-119: confirmar una reserva solicitada que cumpla la disponibilidad; genera el alquiler (RN-39).
    @PatchMapping("/{id}/confirmar")
    @Transactional
    public ReservaDto confirmar(@PathVariable Long id, Authentication auth) {
        Reserva r = buscar(id);
        if (r.getEstado() != EstadoReserva.SOLICITADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se confirman reservas solicitadas");
        }
        ConfiguracionZona cfg = configuracion(r.getZona());
        Evaluacion e = evaluar(r.getZona(), cfg, r.getInicio(), r.getFin());
        if (e.motivo() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.motivo());
        }

        if (r.getValor() > 0) {
            Obligacion o = new Obligacion();
            o.setInmueble(r.getInmueble());
            o.setConcepto(ConceptoObligacion.ALQUILER_ZONA);
            o.setPeriodo(YearMonth.from(r.getInicio()).toString());
            o.setDescripcion("Reserva #" + r.getId() + " · " + r.getZona().getIdentificacion()
                    + " · " + r.getInicio().toLocalDate().format(FECHA));
            o.setValor(r.getValor());
            o.setSaldo(r.getValor());
            o.setFechaVencimiento(r.getInicio().toLocalDate());
            o.setEstado(EstadoObligacion.PENDIENTE);
            o.setCreadaPor(auth.getName());
            obligaciones.save(o);
            r.setObligacion(o);
        }
        r.setEstado(EstadoReserva.CONFIRMADA);
        r.setConfirmadaEn(LocalDateTime.now());
        reservas.save(r);

        auditoria.registrar("CONFIRMAR_RESERVA", "Reserva", r.getId(),
                "Zona " + r.getZona().getIdentificacion() + ", valor " + r.getValor());
        return dto(r, true);
    }

    // Rechazar una solicitud (no se puede confirmar).
    @PatchMapping("/{id}/rechazar")
    @Transactional
    public ReservaDto rechazar(@PathVariable Long id, @RequestParam String motivo) {
        Reserva r = buscar(id);
        String m = motivoValido(motivo, "el rechazo");
        if (r.getEstado() != EstadoReserva.SOLICITADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se rechazan reservas solicitadas");
        }
        r.setEstado(EstadoReserva.RECHAZADA);
        r.setMotivoCierre(m);
        r.setCerradaEn(LocalDateTime.now());
        reservas.save(r);
        auditoria.registrar("RECHAZAR_RESERVA", "Reserva", r.getId(), "Motivo: " + m);
        return dto(r, true);
    }

    // RF-121: finalizar una reserva confirmada indicando la fecha de finalización.
    @PatchMapping("/{id}/finalizar")
    @Transactional
    public ReservaDto finalizar(@PathVariable Long id,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Reserva r = buscar(id);
        if (r.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se finalizan reservas confirmadas");
        }
        LocalDate f = fecha != null ? fecha : LocalDate.now();
        if (f.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de finalización no puede ser futura");
        }
        if (f.isBefore(r.getInicio().toLocalDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de finalización no puede ser anterior a la de la reserva");
        }
        r.setEstado(EstadoReserva.FINALIZADA);
        r.setFechaFinalizacion(f);
        r.setCerradaEn(LocalDateTime.now());
        reservas.save(r);
        auditoria.registrar("FINALIZAR_RESERVA", "Reserva", r.getId(), "Fecha " + f);
        return dto(r, true);
    }

    // RF-122 y RN-40: multa por incumplimiento, imputada al propietario del inmueble.
    @PostMapping("/{id}/multa")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReservaDto multar(@PathVariable Long id, @RequestBody NuevaMulta body, Authentication auth) {
        Reserva r = buscar(id);
        if (r.getEstado() != EstadoReserva.CONFIRMADA && r.getEstado() != EstadoReserva.FINALIZADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se multan reservas confirmadas o finalizadas");
        }
        if (r.getInicio().toLocalDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva aún no ha llegado");
        }
        if (r.getMulta() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esa reserva ya tiene una multa registrada");
        }
        if (body.valor() == null || body.valor() <= 0 || body.valor() > MAX_VALOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de la multa debe ser mayor que cero");
        }
        if (body.motivo() == null || body.motivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la multa");
        }
        String motivo = body.motivo().trim();
        if (motivo.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 150 caracteres");
        }
        Inmueble destino = r.getInmueble();
        if (body.inmuebleId() != null) {
            destino = inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
            if (!destino.isActivo()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese inmueble está desactivado");
            }
        }

        LocalDate hoy = LocalDate.now();
        Obligacion o = new Obligacion();
        o.setInmueble(destino);
        o.setConcepto(ConceptoObligacion.MULTA);
        o.setPeriodo(YearMonth.from(hoy).toString());
        o.setDescripcion("Multa reserva #" + r.getId() + ": " + motivo);
        o.setValor(body.valor());
        o.setSaldo(body.valor());
        o.setFechaVencimiento(hoy.plusDays(15));
        o.setEstado(EstadoObligacion.PENDIENTE);
        o.setCreadaPor(auth.getName());
        obligaciones.save(o);

        r.setMulta(o);
        r.setMotivoMulta(motivo);
        reservas.save(r);

        auditoria.registrar("MULTA_RESERVA", "Reserva", r.getId(),
                "Inmueble " + destino.getIdentificacion() + ", valor " + body.valor());
        return dto(r, true);
    }

    // ---------- Configuración de zonas (administrador) ----------

    @GetMapping("/admin/zonas")
    @Transactional(readOnly = true)
    public List<ZonaDto> zonasAdmin() {
        List<ZonaDto> salida = new ArrayList<>();
        for (Inmueble z : zonasReservables()) {
            salida.add(zonaDto(z, configuraciones.findByZonaId(z.getId()).orElse(null)));
        }
        return salida;
    }

    @PutMapping("/admin/zonas/{id}")
    @Transactional
    public ZonaDto configurarZona(@PathVariable Long id, @RequestBody ConfigZona body) {
        Inmueble z = zonaReservable(id);
        if (body.unidadCobro() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione la unidad de cobro");
        }
        if (body.tarifa() == null || body.tarifa() < 0 || body.tarifa() > MAX_VALOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarifa no es válida");
        }
        int apertura = parseHora(body.horaApertura());
        int cierre = parseHora(body.horaCierre());
        if (apertura >= cierre) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora de cierre debe ser posterior a la de apertura");
        }
        ConfiguracionZona c = configuraciones.findByZonaId(id).orElseGet(() -> {
            ConfiguracionZona nueva = new ConfiguracionZona();
            nueva.setZona(z);
            return nueva;
        });
        c.setUnidadCobro(body.unidadCobro());
        c.setTarifa(body.tarifa());
        c.setAperturaMin(apertura);
        c.setCierreMin(cierre);
        configuraciones.save(c);

        auditoria.registrar("CONFIGURAR_ZONA", "Inmueble", z.getId(),
                body.unidadCobro() + ", tarifa " + body.tarifa());
        return zonaDto(z, c);
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    private Reserva buscar(Long id) {
        return reservas.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    private String motivoValido(String motivo, String de) {
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de " + de);
        }
        String m = motivo.trim();
        if (m.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 150 caracteres");
        }
        return m;
    }

    // RN-37: solo los inmuebles activos cuyo tipo permite reserva.
    private List<Inmueble> zonasReservables() {
        return inmuebles.findByActivo(true).stream()
                .filter(i -> i.getTipoInmueble() != null && i.getTipoInmueble().isPermiteReserva())
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .toList();
    }

    private Inmueble zonaReservable(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione la zona");
        }
        Inmueble z = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Zona no encontrada"));
        if (!z.isActivo() || z.getTipoInmueble() == null || !z.getTipoInmueble().isPermiteReserva()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese inmueble no admite reservas");
        }
        return z;
    }

    private ConfiguracionZona configuracion(Inmueble zona) {
        return configuraciones.findByZonaId(zona.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "La zona aún no tiene tarifa y horario configurados"));
    }

    // Inmuebles con vinculación residente vigente (RN-43).
    private Map<Long, InmuebleOpcion> residencias(Integrante yo) {
        Map<Long, InmuebleOpcion> mapa = new LinkedHashMap<>();
        for (Vinculacion v : vinculaciones.findByIntegranteIdAndEstado(yo.getId(), EstadoVinculacion.VIGENTE)) {
            TipoVinculo t = v.getTipoVinculo();
            if (!RESIDENTES.contains(t)) continue;
            String etiqueta = switch (t) {
                case PROPIETARIO_RESIDENTE -> "Propietario residente";
                case ARRENDATARIO -> "Arrendatario";
                default -> "Residente autorizado";
            };
            mapa.putIfAbsent(v.getInmueble().getId(),
                    new InmuebleOpcion(v.getInmueble().getId(), v.getInmueble().getIdentificacion(), etiqueta));
        }
        return mapa;
    }

    // Reglas del periodo: mismo día, a futuro, dentro del horario y sin cruzar una reserva confirmada (RN-38).
    private Evaluacion evaluar(Inmueble zona, ConfiguracionZona cfg, LocalDateTime inicio, LocalDateTime fin) {
        String unidad = cfg.getUnidadCobro().getEtiqueta();
        List<Reserva> confirmadas = reservas.findByZonaIdAndEstado(zona.getId(), EstadoReserva.CONFIRMADA);
        List<Franja> ocupadas = confirmadas.stream()
                .filter(r -> r.getInicio().toLocalDate().equals(inicio.toLocalDate()))
                .sorted(Comparator.comparing(Reserva::getInicio))
                .map(r -> new Franja(r.getInicio(), r.getFin()))
                .toList();

        if (!fin.isAfter(inicio)) {
            return invalida("La hora final debe ser posterior a la inicial", false, cfg, unidad, ocupadas);
        }
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            return invalida("La reserva debe empezar y terminar el mismo día", false, cfg, unidad, ocupadas);
        }
        if (!inicio.isAfter(LocalDateTime.now())) {
            return invalida("La reserva debe ser en una fecha y hora futuras", false, cfg, unidad, ocupadas);
        }
        int desde = inicio.getHour() * 60 + inicio.getMinute();
        int hasta = fin.getHour() * 60 + fin.getMinute();
        if (desde < cfg.getAperturaMin() || hasta > cfg.getCierreMin()) {
            return invalida("Fuera del horario de la zona (" + hora(cfg.getAperturaMin()) + " a "
                    + hora(cfg.getCierreMin()) + ")", false, cfg, unidad, ocupadas);
        }
        for (Reserva r : confirmadas) {
            if (inicio.isBefore(r.getFin()) && fin.isAfter(r.getInicio())) {
                return invalida("La zona ya está reservada en ese horario", true, cfg, unidad, ocupadas);
            }
        }
        long minutos = ChronoUnit.MINUTES.between(inicio, fin);
        long unidades = switch (cfg.getUnidadCobro()) {
            case HORA -> (minutos + 59) / 60;
            case DIA, EVENTO -> 1L;
        };
        return new Evaluacion(null, false, unidades * cfg.getTarifa(), unidades, cfg.getTarifa(), unidad, ocupadas);
    }

    private Evaluacion invalida(String motivo, boolean conflicto, ConfiguracionZona cfg, String unidad,
                                List<Franja> ocupadas) {
        return new Evaluacion(motivo, conflicto, 0, 0, cfg.getTarifa(), unidad, ocupadas);
    }

    private int parseHora(String texto) {
        try {
            LocalTime t = LocalTime.parse(texto.trim());
            return t.getHour() * 60 + t.getMinute();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La hora debe tener el formato HH:mm");
        }
    }

    private static String hora(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    private ZonaDto zonaDto(Inmueble z, ConfiguracionZona c) {
        String tipo = z.getTipoInmueble() != null ? z.getTipoInmueble().getNombre() : "";
        if (c == null) {
            return new ZonaDto(z.getId(), z.getIdentificacion(), tipo, false, null, null, 0, null, null);
        }
        return new ZonaDto(z.getId(), z.getIdentificacion(), tipo, true, c.getUnidadCobro().name(),
                c.getUnidadCobro().getEtiqueta(), c.getTarifa(), hora(c.getAperturaMin()), hora(c.getCierreMin()));
    }

    private ReservaDto dto(Reserva r, boolean admin) {
        Obligacion o = r.getObligacion();
        boolean cancelable = (r.getEstado() == EstadoReserva.SOLICITADA || r.getEstado() == EstadoReserva.CONFIRMADA)
                && r.getInicio().isAfter(LocalDateTime.now())
                && (o == null || (o.getEstado() == EstadoObligacion.PENDIENTE && o.getSaldo() == o.getValor()));
        String solicitante = admin
                ? r.getSolicitante().getNombres() + " " + r.getSolicitante().getApellidos() : null;
        Obligacion m = r.getMulta();
        return new ReservaDto(r.getId(), r.getZona().getId(), r.getZona().getIdentificacion(), solicitante,
                r.getInmueble().getId(), r.getInmueble().getIdentificacion(), r.getInicio(), r.getFin(),
                r.getFinalidad(), r.getEstado().name(), r.getValor(), o != null ? o.getId() : null,
                r.getMotivoCierre(), cancelable, m != null ? m.getId() : null, m != null ? m.getValor() : 0,
                r.getMotivoMulta(), r.getFechaFinalizacion());
    }
}