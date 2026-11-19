package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.*;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-11 (administrador: periodos, generación, obligaciones individuales, consulta y anulación)
 * y consulta del residente (RF-070 a RF-073).
 * La seguridad por rol está en SecurityConfig: /api/obligaciones/mias* para propietario y
 * arrendatario; todo lo demás cae en /api/** (solo administrador).
 */
@RestController
@RequestMapping("/api/obligaciones")
public class ObligacionController {

    private static final Pattern PERIODO = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final long MAX_VALOR = 1_000_000_000_000L;

    public record ConfigPeriodo(Long valorBase, LocalDate fechaVencimiento) {}

    public record PeriodoDto(String periodo, long valorBase, LocalDate fechaVencimiento,
                             LocalDateTime generadoEn) {}

    public record ResultadoGeneracion(int creadas, int yaExistian, int omitidas) {}

    public record NuevaObligacion(Long inmuebleId, ConceptoObligacion concepto, Long valor,
                                  LocalDate fechaVencimiento, String descripcion) {}

    public record ObligacionDto(Long id, Long inmuebleId, String inmueble, String concepto,
                                String conceptoNombre, String periodo, String descripcion,
                                long valor, long saldo, LocalDate fechaVencimiento, String estado,
                                boolean vencida, long diasMora, String motivoAnulacion) {}

    public record ResumenInmueble(Long inmuebleId, String inmueble, String vinculo,
                                  long saldoPendiente, long saldoVencido, int pendientes) {}

    private record Alcance(Inmueble inmueble, boolean propietario) {}

    private final ObligacionRepository obligaciones;
    private final PeriodoFacturacionRepository periodos;
    private final InmuebleRepository inmuebles;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public ObligacionController(ObligacionRepository obligaciones, PeriodoFacturacionRepository periodos,
                                InmuebleRepository inmuebles, VinculacionRepository vinculaciones,
                                UsuarioRepository usuarios, AuditoriaService auditoria) {
        this.obligaciones = obligaciones;
        this.periodos = periodos;
        this.inmuebles = inmuebles;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // ---------- CU-11: administrador ----------

    @GetMapping("/periodos")
    @Transactional(readOnly = true)
    public List<PeriodoDto> listarPeriodos() {
        return periodos.findAllByOrderByPeriodoDesc().stream().map(this::periodoDto).toList();
    }

    // RF-065: valor base de la cuota y fecha de vencimiento del periodo.
    @PutMapping("/periodos/{periodo}")
    @Transactional
    public PeriodoDto configurar(@PathVariable String periodo, @RequestBody ConfigPeriodo body) {
        String p = periodoValido(periodo);
        if (body.valorBase() == null || body.valorBase() <= 0 || body.valorBase() > MAX_VALOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor base debe ser mayor que cero");
        }
        if (body.fechaVencimiento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de vencimiento es obligatoria");
        }
        if (body.fechaVencimiento().isBefore(YearMonth.parse(p).atDay(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El vencimiento no puede ser anterior al inicio del periodo");
        }
        PeriodoFacturacion pf = periodos.findByPeriodo(p).orElseGet(PeriodoFacturacion::new);
        if (pf.getGeneradoEn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El periodo ya fue generado: su configuración no se puede cambiar");
        }
        pf.setPeriodo(p);
        pf.setValorBase(body.valorBase());
        pf.setFechaVencimiento(body.fechaVencimiento());
        periodos.save(pf);

        auditoria.registrar("CONFIGURAR", "PeriodoFacturacion", pf.getId(),
                "Periodo " + p + ", valor base " + pf.getValorBase());
        return periodoDto(pf);
    }

    // RF-066: genera la cuota de los inmuebles activos cuyo tipo tiene generaCuota (RN-41).
    @PostMapping("/periodos/{periodo}/generar")
    @Transactional
    public ResultadoGeneracion generar(@PathVariable String periodo, Authentication auth) {
        String p = periodoValido(periodo);
        PeriodoFacturacion pf = periodos.findByPeriodo(p).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Primero configura el valor base del periodo"));

        int creadas = 0, yaExistian = 0, omitidas = 0;
        List<Inmueble> candidatos = inmuebles.findByActivo(true).stream()
                .filter(i -> i.getTipoInmueble() != null && i.getTipoInmueble().isGeneraCuota())
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .toList();

        for (Inmueble i : candidatos) {
            if (obligaciones.existsByInmuebleIdAndConceptoAndPeriodoAndEstadoNot(
                    i.getId(), ConceptoObligacion.CUOTA_ADMINISTRACION, p, EstadoObligacion.ANULADA)) {
                yaExistian++;
                continue;
            }
            long valor = cuota(pf.getValorBase(), i.getCoeficiente());
            if (valor <= 0) {
                omitidas++;
                continue;
            }
            Obligacion o = nueva(i, ConceptoObligacion.CUOTA_ADMINISTRACION, p, valor,
                    pf.getFechaVencimiento(), null, auth.getName());
            obligaciones.save(o);
            creadas++;
        }

        pf.setGeneradoEn(LocalDateTime.now());
        periodos.save(pf);

        auditoria.registrar("GENERAR", "PeriodoFacturacion", pf.getId(),
                "Periodo " + p + ": " + creadas + " creadas, " + yaExistian + " ya existían, "
                        + omitidas + " omitidas");
        return new ResultadoGeneracion(creadas, yaExistian, omitidas);
    }

    // RF-067: obligación individual (cuota extraordinaria o multa).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ObligacionDto crear(@RequestBody NuevaObligacion body, Authentication auth) {
        if (body.inmuebleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seleccione el inmueble");
        }
        if (body.concepto() != ConceptoObligacion.CUOTA_EXTRAORDINARIA
                && body.concepto() != ConceptoObligacion.MULTA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se registran manualmente cuotas extraordinarias o multas");
        }
        if (body.valor() == null || body.valor() <= 0 || body.valor() > MAX_VALOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor debe ser mayor que cero");
        }
        if (body.fechaVencimiento() == null || body.fechaVencimiento().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de vencimiento no puede ser anterior a hoy");
        }
        String detalle = texto(body.descripcion(), "Escriba el detalle de la obligación", 200);

        Inmueble inmueble = inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        if (!inmueble.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble está desactivado");
        }

        String periodo = YearMonth.from(body.fechaVencimiento()).toString();
        Obligacion o = nueva(inmueble, body.concepto(), periodo, body.valor(),
                body.fechaVencimiento(), detalle, auth.getName());
        obligaciones.save(o);

        auditoria.registrar("CREAR", "Obligacion", o.getId(),
                body.concepto().name() + ", inmueble " + inmueble.getIdentificacion() + ", valor " + o.getValor());
        return dto(o);
    }

    // RF-068
    @GetMapping
    @Transactional(readOnly = true)
    public List<ObligacionDto> listar(@RequestParam(required = false) String periodo,
                                      @RequestParam(required = false) Long inmuebleId,
                                      @RequestParam(required = false) EstadoObligacion estado,
                                      @RequestParam(required = false) ConceptoObligacion concepto) {
        return obligaciones.findAllByOrderByIdDesc().stream()
                .filter(o -> periodo == null || periodo.isBlank() || o.getPeriodo().equals(periodo))
                .filter(o -> inmuebleId == null || o.getInmueble().getId().equals(inmuebleId))
                .filter(o -> estado == null || o.getEstado() == estado)
                .filter(o -> concepto == null || o.getConcepto() == concepto)
                .map(this::dto)
                .toList();
    }

    // RF-069 y RN-27: anular con motivo, solo si no tiene abonos.
    @PatchMapping("/{id}/anular")
    @Transactional
    public ObligacionDto anular(@PathVariable Long id, @RequestParam String motivo) {
        Obligacion o = obligaciones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Obligación no encontrada"));
        String m = texto(motivo, "Indique el motivo de la anulación", 200);
        if (o.getEstado() == EstadoObligacion.ANULADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La obligación ya está anulada");
        }
        if (o.getEstado() == EstadoObligacion.PAGADA || o.getSaldo() != o.getValor()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede anular una obligación con pagos o abonos");
        }
        o.setEstado(EstadoObligacion.ANULADA);
        o.setMotivoAnulacion(m);
        o.setAnuladaEn(LocalDateTime.now());
        obligaciones.save(o);

        auditoria.registrar("ANULAR", "Obligacion", o.getId(), "Motivo: " + m);
        return dto(o);
    }

    // ---------- Consulta del residente (RF-070 a RF-073) ----------

    // Obligaciones de los inmuebles del usuario. El propietario ve todo; el arrendatario, solo la cuota ordinaria.
    @GetMapping("/mias")
    @Transactional(readOnly = true)
    public List<ObligacionDto> mias(@RequestParam(required = false) Long inmuebleId, Authentication auth) {
        Map<Long, Alcance> alcance = alcance(integranteActual(auth));
        if (inmuebleId != null && !alcance.containsKey(inmuebleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene vinculación vigente con ese inmueble");
        }
        return visibles(alcance, inmuebleId).stream().map(this::dto).toList();
    }

    // RF-071: saldo pendiente por inmueble.
    @GetMapping("/mias/resumen")
    @Transactional(readOnly = true)
    public List<ResumenInmueble> resumen(Authentication auth) {
        Map<Long, Alcance> alcance = alcance(integranteActual(auth));
        List<Obligacion> todas = visibles(alcance, null);
        LocalDate hoy = LocalDate.now();

        List<ResumenInmueble> lista = new ArrayList<>();
        for (Alcance a : alcance.values()) {
            long saldo = 0, vencido = 0;
            int pendientes = 0;
            for (Obligacion o : todas) {
                if (!o.getInmueble().getId().equals(a.inmueble().getId())
                        || o.getEstado() != EstadoObligacion.PENDIENTE) {
                    continue;
                }
                saldo += o.getSaldo();
                pendientes++;
                if (o.getFechaVencimiento().isBefore(hoy)) {
                    vencido += o.getSaldo();
                }
            }
            lista.add(new ResumenInmueble(a.inmueble().getId(), a.inmueble().getIdentificacion(),
                    a.propietario() ? "Propietario" : "Arrendatario", saldo, vencido, pendientes));
        }
        lista.sort(Comparator.comparing(ResumenInmueble::inmueble));
        return lista;
    }

    // ---------- Ayudas ----------

    private String periodoValido(String periodo) {
        if (periodo == null || !PERIODO.matcher(periodo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo debe tener el formato AAAA-MM");
        }
        return periodo;
    }

    private String texto(String s, String mensajeVacio, int max) {
        if (s == null || s.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeVacio);
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El texto no puede pasar de " + max + " caracteres");
        }
        return t;
    }

    // RN-41: valor base x coeficiente (porcentaje) / 100, redondeado al peso.
    private long cuota(long valorBase, BigDecimal coeficiente) {
        if (coeficiente == null || coeficiente.signum() <= 0) return 0;
        return BigDecimal.valueOf(valorBase)
                .multiply(coeficiente)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private Obligacion nueva(Inmueble inmueble, ConceptoObligacion concepto, String periodo, long valor,
                             LocalDate vencimiento, String descripcion, String usuario) {
        Obligacion o = new Obligacion();
        o.setInmueble(inmueble);
        o.setConcepto(concepto);
        o.setPeriodo(periodo);
        o.setValor(valor);
        o.setSaldo(valor);
        o.setFechaVencimiento(vencimiento);
        o.setDescripcion(descripcion);
        o.setCreadaPor(usuario);
        return o;
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
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

    private List<Obligacion> visibles(Map<Long, Alcance> alcance, Long inmuebleId) {
        List<Long> ids = inmuebleId != null ? List.of(inmuebleId) : new ArrayList<>(alcance.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return obligaciones.findByInmuebleIdInOrderByIdDesc(ids).stream()
                .filter(o -> o.getEstado() != EstadoObligacion.ANULADA)
                .filter(o -> alcance.get(o.getInmueble().getId()).propietario()
                        || o.getConcepto().isOrdinaria())
                .toList();
    }

    private PeriodoDto periodoDto(PeriodoFacturacion p) {
        return new PeriodoDto(p.getPeriodo(), p.getValorBase(), p.getFechaVencimiento(), p.getGeneradoEn());
    }

    private ObligacionDto dto(Obligacion o) {
        LocalDate hoy = LocalDate.now();
        boolean vencida = o.getEstado() == EstadoObligacion.PENDIENTE && o.getSaldo() > 0
                && o.getFechaVencimiento().isBefore(hoy);
        long dias = vencida ? ChronoUnit.DAYS.between(o.getFechaVencimiento(), hoy) : 0;
        return new ObligacionDto(o.getId(), o.getInmueble().getId(), o.getInmueble().getIdentificacion(),
                o.getConcepto().name(), o.getConcepto().getEtiqueta(), o.getPeriodo(), o.getDescripcion(),
                o.getValor(), o.getSaldo(), o.getFechaVencimiento(), o.getEstado().name(),
                vencida, dias, o.getMotivoAnulacion());
    }
}