package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.pasarela.PasarelaPago;
import com.proyecto.plataforma_ph.pasarela.PasarelaSimulada;
import com.proyecto.plataforma_ph.repository.*;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-12 (propietario y arrendatario: pagar por pasarela, abonar, ver resultado, historial,
 * comprobante) y CU-13 (administrador: pago presencial, recibo de caja, anulación, listado).
 * La seguridad por rol está en SecurityConfig; aquí se valida el alcance de cada inmueble
 * y que el arrendatario pague solo expensas ordinarias (RN-22).
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final long MAX_VALOR = 1_000_000_000_000L;
    private static final Set<MedioPago> MEDIOS_PRESENCIALES =
            Set.of(MedioPago.EFECTIVO, MedioPago.TRANSFERENCIA, MedioPago.CHEQUE);

    public record ItemPago(Long obligacionId, Long valor) {}

    public record NuevoPagoPasarela(Long inmuebleId, List<ItemPago> items) {}

    public record NuevoPagoPresencial(Long inmuebleId, MedioPago medio, LocalDate fecha,
                                      String pagador, List<ItemPago> items) {}

    public record InicioPasarela(Long pagoId, String referencia, String url) {}

    public record DetalleDto(Long obligacionId, String concepto, String periodo, long valor) {}

    public record InfoPasarela(Long pagoId, String referencia, String inmueble, long valor,
                               String estado, String proveedor, List<DetalleDto> detalles) {}

    public record PagoDto(Long id, String numero, Long inmuebleId, String inmueble, String canal,
                          String medio, String estado, long valor, LocalDate fecha,
                          LocalDateTime creadoEn, LocalDateTime confirmadoEn, String pagador,
                          String registradoPor, String referencia, String transaccionId,
                          String motivoAnulacion, List<DetalleDto> detalles) {}

    private record Alcance(Inmueble inmueble, boolean propietario) {}

    private final PagoRepository pagos;
    private final ObligacionRepository obligaciones;
    private final InmuebleRepository inmuebles;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final PasarelaPago pasarela;
    private final NotificacionService notificaciones;

    public PagoController(PagoRepository pagos, ObligacionRepository obligaciones,
                          InmuebleRepository inmuebles, VinculacionRepository vinculaciones,
                          UsuarioRepository usuarios, AuditoriaService auditoria, PasarelaPago pasarela,
                          NotificacionService notificaciones) {
        this.pagos = pagos;
        this.obligaciones = obligaciones;
        this.inmuebles = inmuebles;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.pasarela = pasarela;
        this.notificaciones = notificaciones;
    }

    // ---------- CU-12: propietario y arrendatario ----------

    // RF-074, RF-075 y RF-123: pagar o abonar obligaciones por la pasarela.
    @PostMapping("/pasarela")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InicioPasarela iniciarPasarela(@RequestBody NuevoPagoPasarela body, Authentication auth) {
        Integrante yo = integranteActual(auth);
        if (body.inmuebleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione el inmueble");
        }
        Alcance a = alcance(yo).get(body.inmuebleId());
        if (a == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene vinculación vigente con ese inmueble");
        }

        Pago p = new Pago();
        p.setInmueble(a.inmueble());
        p.setCanal(CanalPago.PASARELA);
        p.setMedio(MedioPago.PASARELA);
        p.setEstado(EstadoPago.PENDIENTE);
        p.setFecha(LocalDate.now());
        p.setPagador(nombre(yo));
        p.setRegistradoPor(auth.getName());
        p.setReferencia("PH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        armarDetalles(p, body.items(), !a.propietario());
        pagos.save(p);

        PasarelaPago.SesionPago sesion = pasarela.iniciar(p, "/mis-obligaciones?pago=" + p.getId());
        auditoria.registrar("INICIAR_PAGO", "Pago", p.getId(),
                "Inmueble " + a.inmueble().getIdentificacion() + ", valor " + total(p));
        return new InicioPasarela(p.getId(), p.getReferencia(), sesion.url());
    }

    // Datos del pago para la página de la pasarela.
    @GetMapping("/pasarela/{referencia}")
    @Transactional(readOnly = true)
    public InfoPasarela infoPasarela(@PathVariable String referencia, Authentication auth) {
        Pago p = porReferencia(referencia);
        exigirAcceso(p, auth);
        return info(p);
    }

    // Solo de la simulación: la persona aprueba o rechaza en la "pasarela".
    @PostMapping("/pasarela/{referencia}/simulada")
    @Transactional
    public InfoPasarela responderSimulada(@PathVariable String referencia, @RequestParam boolean aprobado,
                                          Authentication auth) {
        if (!(pasarela instanceof PasarelaSimulada simulada)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No disponible");
        }
        Pago p = porReferencia(referencia);
        exigirAcceso(p, auth);
        if (p.getEstado() != EstadoPago.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese pago ya fue procesado");
        }
        simulada.responder(p, aprobado);
        pagos.save(p);
        return info(p);
    }

    // RF-076 y RN-23: confirma el pago solo si la pasarela devuelve un resultado válido.
    @PostMapping("/{id}/verificar")
    @Transactional
    public PagoDto verificar(@PathVariable Long id, Authentication auth) {
        Pago p = buscar(id);
        exigirAcceso(p, auth);
        if (p.getCanal() != CanalPago.PASARELA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese pago no es de la pasarela");
        }
        if (p.getEstado() == EstadoPago.PENDIENTE) {
            PasarelaPago.ResultadoPasarela r = pasarela.consultar(p);
            if (r.estado() == PasarelaPago.Estado.APROBADA) {
                aplicar(p);
                p.setEstado(EstadoPago.CONFIRMADO);
                p.setConfirmadoEn(LocalDateTime.now());
                p.setFecha(LocalDate.now());
                p.setTransaccionId(r.transaccionId());
                pagos.save(p);
                auditoria.registrar("CONFIRMAR_PAGO", "Pago", p.getId(),
                        "Inmueble " + p.getInmueble().getIdentificacion() + ", valor " + total(p));
                avisarPago(p);
            } else if (r.estado() == PasarelaPago.Estado.RECHAZADA) {
                p.setEstado(EstadoPago.RECHAZADO);
                p.setTransaccionId(r.transaccionId());
                pagos.save(p);
                auditoria.registrar("RECHAZAR_PAGO", "Pago", p.getId(),
                        "Inmueble " + p.getInmueble().getIdentificacion());
            }
        }
        return dto(p, false);
    }

    // RF-078: historial de pagos de mis inmuebles, filtrable por periodo.
    @GetMapping("/mios")
    @Transactional(readOnly = true)
    public List<PagoDto> mios(@RequestParam(required = false) Long inmuebleId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                              Authentication auth) {
        Map<Long, Alcance> alcance = alcance(integranteActual(auth));
        if (inmuebleId != null && !alcance.containsKey(inmuebleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene vinculación vigente con ese inmueble");
        }
        List<Long> ids = inmuebleId != null ? List.of(inmuebleId) : new ArrayList<>(alcance.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return pagos.findByInmuebleIdInOrderByIdDesc(ids).stream()
                .filter(p -> visibleParaResidente(p, alcance.get(p.getInmueble().getId())))
                .filter(p -> desde == null || !p.getFecha().isBefore(desde))
                .filter(p -> hasta == null || !p.getFecha().isAfter(hasta))
                .map(p -> dto(p, false))
                .toList();
    }

    // RF-077 y RF-080: comprobante de pago o recibo de caja (página imprimible).
    @GetMapping("/{id}/comprobante")
    @Transactional(readOnly = true)
    public ResponseEntity<String> comprobante(@PathVariable Long id, Authentication auth) {
        Pago p = buscar(id);
        if (!esAdmin(auth)) {
            exigirAcceso(p, auth);
        }
        if (p.getEstado() != EstadoPago.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo los pagos confirmados tienen comprobante");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .body(htmlComprobante(p));
    }

    // ---------- CU-13: administrador (cae en /api/**) ----------

    // RF-079 y RN-24: pago presencial, confirmado al registrarse.
    @PostMapping("/presencial")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PagoDto registrarPresencial(@RequestBody NuevoPagoPresencial body, Authentication auth) {
        if (body.inmuebleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione el inmueble");
        }
        if (body.medio() == null || !MEDIOS_PRESENCIALES.contains(body.medio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El medio debe ser efectivo, transferencia o cheque");
        }
        LocalDate fecha = body.fecha() != null ? body.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha del pago no puede ser futura");
        }
        if (body.pagador() == null || body.pagador().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique quién paga");
        }
        String pagador = body.pagador().trim();
        if (pagador.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del pagador es muy largo");
        }
        Inmueble inmueble = inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));

        Pago p = new Pago();
        p.setInmueble(inmueble);
        p.setCanal(CanalPago.PRESENCIAL);
        p.setMedio(body.medio());
        p.setFecha(fecha);
        p.setPagador(pagador);
        p.setRegistradoPor(auth.getName());
        armarDetalles(p, body.items(), false);
        aplicar(p);
        p.setEstado(EstadoPago.CONFIRMADO);
        p.setConfirmadoEn(LocalDateTime.now());
        pagos.save(p);

        auditoria.registrar("REGISTRAR_PAGO", "Pago", p.getId(),
                "Inmueble " + inmueble.getIdentificacion() + ", " + body.medio() + ", valor " + total(p));
        avisarPago(p);
        return dto(p, true);
    }

    // RF-081 y RN-27: anular un pago presencial con motivo; devuelve el saldo a las obligaciones.
    @PatchMapping("/{id}/anular")
    @Transactional
    public PagoDto anular(@PathVariable Long id, @RequestParam String motivo) {
        Pago p = buscar(id);
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la anulación");
        }
        String m = motivo.trim();
        if (m.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 200 caracteres");
        }
        if (p.getCanal() != CanalPago.PRESENCIAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se anulan pagos presenciales");
        }
        if (p.getEstado() != EstadoPago.CONFIRMADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pago ya está anulado");
        }
        for (PagoDetalle d : p.getDetalles()) {
            Obligacion o = d.getObligacion();
            o.setSaldo(o.getSaldo() + d.getValor());
            if (o.getEstado() == EstadoObligacion.PAGADA) {
                o.setEstado(EstadoObligacion.PENDIENTE);
            }
            obligaciones.save(o);
        }
        p.setEstado(EstadoPago.ANULADO);
        p.setMotivoAnulacion(m);
        p.setAnuladoEn(LocalDateTime.now());
        pagos.save(p);

        auditoria.registrar("ANULAR_PAGO", "Pago", p.getId(), "Motivo: " + m);
        return dto(p, true);
    }

    // RF-082: pagos recibidos en un periodo, diferenciados por canal.
    @GetMapping
    @Transactional(readOnly = true)
    public List<PagoDto> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) CanalPago canal,
            @RequestParam(required = false) EstadoPago estado,
            @RequestParam(required = false) Long inmuebleId) {
        return pagos.findAllByOrderByIdDesc().stream()
                .filter(p -> desde == null || !p.getFecha().isBefore(desde))
                .filter(p -> hasta == null || !p.getFecha().isAfter(hasta))
                .filter(p -> canal == null || p.getCanal() == canal)
                .filter(p -> estado == null || p.getEstado() == estado)
                .filter(p -> inmuebleId == null || p.getInmueble().getId().equals(inmuebleId))
                .map(p -> dto(p, true))
                .toList();
    }

    // ---------- Ayudas ----------

    // CU-17: aviso de pago confirmado al obligado del pago (RN-28). Un fallo no afecta al pago.
    private void avisarPago(Pago p) {
        notificaciones.avisar(EventoNotificacion.PAGO_CONFIRMADO, p.getInmueble(),
                "Pago confirmado " + numero(p),
                "Se confirmó el pago " + numero(p) + " del inmueble "
                        + p.getInmueble().getIdentificacion() + " por $ " + total(p) + ".");
    }

    private Pago buscar(Long id) {
        return pagos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private Pago porReferencia(String referencia) {
        return pagos.findByReferencia(referencia).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    private String nombre(Integrante i) {
        return i.getNombres() + " " + i.getApellidos();
    }

    // Inmuebles con vinculación vigente de propiedad o de arrendamiento.
    private Map<Long, Alcance> alcance(Integrante yo) {
        Map<Long, Alcance> mapa = new LinkedHashMap<>();
        for (Vinculacion v : vinculaciones.findByIntegranteIdAndEstado(yo.getId(), EstadoVinculacion.VIGENTE)) {
            TipoVinculo t = v.getTipoVinculo();
            Inmueble i = v.getInmueble();
            if (t == TipoVinculo.PROPIETARIO_RESIDENTE || t == TipoVinculo.PROPIETARIO_NO_RESIDENTE) {
                mapa.put(i.getId(), new Alcance(i, true));
            } else if (t == TipoVinculo.ARRENDATARIO) {
                mapa.putIfAbsent(i.getId(), new Alcance(i, false));
            }
        }
        return mapa;
    }

    private void exigirAcceso(Pago p, Authentication auth) {
        Alcance a = alcance(integranteActual(auth)).get(p.getInmueble().getId());
        if (a == null || !visibleParaResidente(p, a)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a ese pago");
        }
    }

    // El arrendatario solo ve pagos cuyas obligaciones sean todas ordinarias.
    private boolean visibleParaResidente(Pago p, Alcance a) {
        if (a == null) return false;
        if (a.propietario()) return true;
        return p.getDetalles().stream().allMatch(d -> d.getObligacion().getConcepto().isPagableArrendatario());
    }

    private void armarDetalles(Pago p, List<ItemPago> items, boolean soloOrdinarias) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione al menos una obligación");
        }
        Set<Long> vistos = new HashSet<>();
        for (ItemPago it : items) {
            if (it.obligacionId() == null || it.valor() == null || it.valor() <= 0 || it.valor() > MAX_VALOR) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada obligación necesita un valor mayor que cero");
            }
            if (!vistos.add(it.obligacionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hay una obligación repetida");
            }
            Obligacion o = obligaciones.findById(it.obligacionId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));
            if (!o.getInmueble().getId().equals(p.getInmueble().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La obligación " + o.getId() + " no pertenece a ese inmueble");
            }
            if (o.getEstado() != EstadoObligacion.PENDIENTE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La obligación " + o.getId() + " ya no está pendiente");
            }
            if (soloOrdinarias && !o.getConcepto().isPagableArrendatario()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Como arrendatario solo puede pagar la cuota de administración y el alquiler de zonas comunes");
            }
            if (it.valor() > o.getSaldo()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El valor supera el saldo de la obligación " + o.getId() + " (" + o.getSaldo() + ")");
            }
            PagoDetalle d = new PagoDetalle();
            d.setPago(p);
            d.setObligacion(o);
            d.setValor(it.valor());
            p.getDetalles().add(d);
        }
    }

    // RN-42: cada pago confirmado baja el saldo; en cero la obligación queda pagada.
    private void aplicar(Pago p) {
        for (PagoDetalle d : p.getDetalles()) {
            Obligacion o = d.getObligacion();
            if (o.getEstado() != EstadoObligacion.PENDIENTE || d.getValor() > o.getSaldo()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La obligación " + o.getId() + " cambió y ya no admite ese valor. Revise sus obligaciones");
            }
        }
        for (PagoDetalle d : p.getDetalles()) {
            Obligacion o = d.getObligacion();
            o.setSaldo(o.getSaldo() - d.getValor());
            if (o.getSaldo() == 0) {
                o.setEstado(EstadoObligacion.PAGADA);
            }
            obligaciones.save(o);
        }
    }

    private long total(Pago p) {
        return p.getDetalles().stream().mapToLong(PagoDetalle::getValor).sum();
    }

    private String numero(Pago p) {
        return (p.getCanal() == CanalPago.PRESENCIAL ? "RC-" : "CP-") + String.format("%06d", p.getId());
    }

    private List<DetalleDto> detalles(Pago p) {
        return p.getDetalles().stream()
                .map(d -> new DetalleDto(d.getObligacion().getId(), d.getObligacion().getConcepto().getEtiqueta(),
                        d.getObligacion().getPeriodo(), d.getValor()))
                .toList();
    }

    private InfoPasarela info(Pago p) {
        return new InfoPasarela(p.getId(), p.getReferencia(), p.getInmueble().getIdentificacion(),
                total(p), p.getEstado().name(), pasarela.nombre(), detalles(p));
    }

    private PagoDto dto(Pago p, boolean completo) {
        return new PagoDto(p.getId(), numero(p), p.getInmueble().getId(), p.getInmueble().getIdentificacion(),
                p.getCanal().name(), p.getMedio().name(), p.getEstado().name(), total(p), p.getFecha(),
                p.getCreadoEn(), p.getConfirmadoEn(), p.getPagador(),
                completo ? p.getRegistradoPor() : null, p.getReferencia(), p.getTransaccionId(),
                p.getMotivoAnulacion(), detalles(p));
    }

    // ---------- Comprobante imprimible ----------

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String medioEtiqueta(MedioPago m) {
        return switch (m) {
            case EFECTIVO -> "Efectivo";
            case TRANSFERENCIA -> "Transferencia";
            case CHEQUE -> "Cheque";
            case PASARELA -> "Pasarela de pagos";
        };
    }

    private String htmlComprobante(Pago p) {
        boolean presencial = p.getCanal() == CanalPago.PRESENCIAL;
        String titulo = presencial ? "Recibo de caja" : "Comprobante de pago";
        NumberFormat pesos = NumberFormat.getInstance(Locale.forLanguageTag("es-CO"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        StringBuilder filas = new StringBuilder();
        for (PagoDetalle d : p.getDetalles()) {
            filas.append("<tr><td>").append(h(d.getObligacion().getConcepto().getEtiqueta()))
                    .append("</td><td>").append(h(d.getObligacion().getPeriodo()))
                    .append("</td><td class=\"n\">$ ").append(pesos.format(d.getValor()))
                    .append("</td></tr>");
        }

        StringBuilder s = new StringBuilder();
        s.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">");
        s.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        s.append("<title>").append(h(titulo)).append(" ").append(h(numero(p))).append("</title>");
        s.append("<style>");
        s.append("body{font-family:'Nunito','Segoe UI',system-ui,-apple-system,sans-serif;color:#1E2923;margin:0;padding:24px;background:#FAFDF9;line-height:1.5;-webkit-print-color-adjust:exact;print-color-adjust:exact}");
        s.append(".hoja{max-width:640px;margin:0 auto;background:#fff;border:1px solid #DCEBDD;border-radius:20px;padding:30px;box-shadow:0 2px 14px rgba(47,107,79,.07)}");
        s.append(".marca{display:flex;align-items:center;gap:10px;margin:0 0 18px;padding-bottom:16px;border-bottom:1px solid #DCEBDD}");
        s.append(".marca svg{width:40px;height:40px}.marca span{font-weight:800;letter-spacing:.16em;font-size:1.05rem;color:#2B4A3B}");
        s.append("h1{font-size:1.45rem;margin:0 0 2px;color:#2B4A3B;letter-spacing:-.01em}.sub{color:#4A5D52;margin:0 0 12px}");
        s.append(".chip{display:inline-block;background:#E3F1E4;color:#2F6B4F;font-size:.8rem;font-weight:700;padding:4px 12px;border-radius:999px;margin:0 0 18px}");
        s.append("dl{display:grid;grid-template-columns:150px 1fr;gap:6px 12px;margin:0 0 20px;background:#EEF7EE;border:1px solid #DCEBDD;border-radius:14px;padding:16px 18px}");
        s.append("dt{color:#4A5D52}dd{margin:0;font-weight:600}");
        s.append("table{width:100%;border-collapse:collapse;margin-bottom:10px}");
        s.append("th{background:#EEF7EE;color:#4A5D52;font-weight:600}");
        s.append("th,td{padding:10px 12px;border-bottom:1px solid #DCEBDD;text-align:left}");
        s.append(".n{text-align:right;white-space:nowrap}");
        s.append(".total{font-size:1.25rem;font-weight:800;text-align:right;color:#2F6B4F}");
        s.append(".pie{color:#4A5D52;font-size:.8rem;margin-top:18px}");
        s.append("button{margin-top:16px;padding:12px 20px;border:0;border-radius:12px;background:#95D5B2;color:#1E2923;font-size:1rem;font-weight:700;font-family:inherit;cursor:pointer;transition:background .18s}");
        s.append("button:hover{background:#74C69D}");
        s.append("@media (max-width:520px){dl{grid-template-columns:1fr}}");
        s.append("@media print{body{background:#fff;padding:0}.hoja{border:0;box-shadow:none}button{display:none}}</style></head><body>");
        s.append("<div class=\"hoja\">");
        s.append("<div class=\"marca\"><svg viewBox='0 0 48 48' fill='none' xmlns='http://www.w3.org/2000/svg' aria-hidden='true'>");
        s.append("<rect width='48' height='48' rx='14' fill='#E3F1E4'/>");
        s.append("<path d='M9 24 24 11 39 24' stroke='#2F6B4F' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'/>");
        s.append("<circle cx='18' cy='29' r='3.8' fill='#95D5B2'/><circle cx='30' cy='29' r='3.8' fill='#74C69D'/>");
        s.append("<path d='M11.5 40c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6' stroke='#95D5B2' stroke-width='2.4' stroke-linecap='round'/>");
        s.append("<path d='M23.5 40c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6' stroke='#74C69D' stroke-width='2.4' stroke-linecap='round'/>");
        s.append("</svg><span>CONVIVIR</span></div>");
        s.append("<h1>").append(h(titulo)).append("</h1>");
        s.append("<p class=\"sub\">N.° ").append(h(numero(p))).append("</p>");
        s.append("<span class=\"chip\">Pago confirmado</span><dl>");
        s.append("<dt>Inmueble</dt><dd>").append(h(p.getInmueble().getIdentificacion())).append("</dd>");
        s.append("<dt>Fecha del pago</dt><dd>").append(p.getFecha().format(fmt)).append("</dd>");
        s.append("<dt>Pagador</dt><dd>").append(h(p.getPagador())).append("</dd>");
        s.append("<dt>Medio de pago</dt><dd>").append(h(medioEtiqueta(p.getMedio()))).append("</dd>");
        if (!presencial) {
            s.append("<dt>Referencia</dt><dd>").append(h(p.getReferencia())).append("</dd>");
            s.append("<dt>Transacción</dt><dd>").append(h(p.getTransaccionId())).append("</dd>");
        } else {
            s.append("<dt>Recibido por</dt><dd>Administración</dd>");
        }
        s.append("</dl><table><thead><tr><th>Concepto</th><th>Periodo</th><th class=\"n\">Valor pagado</th></tr></thead><tbody>");
        s.append(filas).append("</tbody></table>");
        s.append("<p class=\"total\">Total: $ ").append(pesos.format(total(p))).append("</p>");
        s.append("<p class=\"pie\">Documento generado por la plataforma de gestión de propiedad horizontal.</p>");
        s.append("<button onclick=\"window.print()\">Imprimir o guardar como PDF</button></div></body></html>");
        return s.toString();
    }
}
