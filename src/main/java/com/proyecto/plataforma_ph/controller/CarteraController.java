package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoPago;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.PagoRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-14: cartera por inmueble (RN-25).
 * Consulta (listado y detalle): administrador y consejo, solo lectura (RN-31).
 * Exportar, paz y salvo e intereses de mora: solo administrador (caen en /api/**).
 */
@RestController
@RequestMapping("/api/cartera")
public class CarteraController {

    private static final Pattern PERIODO = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final BigDecimal TASA_MAXIMA = new BigDecimal("10");

    public record FilaCartera(Long inmuebleId, String inmueble, String tipo, long saldoPendiente,
                              long saldoVencido, int obligaciones, int diasMora) {}

    public record LineaDeuda(Long id, String concepto, String periodo, String descripcion, long valor,
                             long saldo, LocalDate fechaVencimiento, int diasMora) {}

    public record DetalleCartera(Long inmuebleId, String inmueble, long saldoPendiente,
                                 long saldoVencido, List<LineaDeuda> deudas) {}

    public record GenerarIntereses(BigDecimal tasaMensual) {}

    public record ResultadoIntereses(String periodo, int generados, int yaExistian, int omitidos, long valorTotal) {}

    private final ObligacionRepository obligaciones;
    private final InmuebleRepository inmuebles;
    private final PagoRepository pagos;
    private final VinculacionRepository vinculaciones;
    private final AuditoriaService auditoria;

    public CarteraController(ObligacionRepository obligaciones, InmuebleRepository inmuebles,
                             PagoRepository pagos, VinculacionRepository vinculaciones,
                             AuditoriaService auditoria) {
        this.obligaciones = obligaciones;
        this.inmuebles = inmuebles;
        this.pagos = pagos;
        this.vinculaciones = vinculaciones;
        this.auditoria = auditoria;
    }

    // ---------- RF-083 y RF-085: inmuebles con saldo, filtrables por mora ----------

    @GetMapping
    @Transactional(readOnly = true)
    public List<FilaCartera> listar(@RequestParam(required = false) String periodo,
                                    @RequestParam(required = false) Integer minDias,
                                    @RequestParam(required = false) Integer maxDias) {
        return filas(periodo(periodo), minDias, maxDias);
    }

    // ---------- RF-084: detalle de la deuda de un inmueble por periodo y concepto ----------

    @GetMapping("/inmuebles/{id}")
    @Transactional(readOnly = true)
    public DetalleCartera detalle(@PathVariable Long id, @RequestParam(required = false) String periodo) {
        String per = periodo(periodo);
        Inmueble inm = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        LocalDate hoy = LocalDate.now();
        List<LineaDeuda> lineas = obligaciones.findByInmuebleIdInOrderByIdDesc(List.of(id)).stream()
                .filter(this::pendiente)
                .filter(o -> per == null || per.equals(o.getPeriodo()))
                .sorted(Comparator.comparing(Obligacion::getPeriodo).thenComparing(Obligacion::getId))
                .map(o -> new LineaDeuda(o.getId(), o.getConcepto().getEtiqueta(), o.getPeriodo(),
                        o.getDescripcion(), o.getValor(), o.getSaldo(), o.getFechaVencimiento(), dias(o, hoy)))
                .toList();
        long saldo = 0;
        long vencido = 0;
        for (LineaDeuda l : lineas) {
            saldo += l.saldo();
            if (l.diasMora() > 0) vencido += l.saldo();
        }
        return new DetalleCartera(inm.getId(), inm.getIdentificacion(), saldo, vencido, lineas);
    }

    // ---------- RF-086: exportar el informe de cartera (CSV que abre en Excel) ----------

    @GetMapping("/exportar")
    @Transactional
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String periodo,
                                           @RequestParam(required = false) Integer minDias,
                                           @RequestParam(required = false) Integer maxDias) {
        String per = periodo(periodo);
        List<FilaCartera> filas = filas(per, minDias, maxDias);
        StringBuilder s = new StringBuilder("\uFEFF");
        s.append("Inmueble;Tipo;Saldo pendiente;Saldo vencido;Obligaciones pendientes;Dias de mora\r\n");
        long totalSaldo = 0;
        long totalVencido = 0;
        for (FilaCartera f : filas) {
            s.append(csv(f.inmueble())).append(';').append(csv(f.tipo())).append(';')
                    .append(f.saldoPendiente()).append(';').append(f.saldoVencido()).append(';')
                    .append(f.obligaciones()).append(';').append(f.diasMora()).append("\r\n");
            totalSaldo += f.saldoPendiente();
            totalVencido += f.saldoVencido();
        }
        s.append("TOTAL;;").append(totalSaldo).append(';').append(totalVencido).append(";;\r\n");

        auditoria.registrar("EXPORTAR_CARTERA", "Cartera", 0L,
                "Periodo " + (per != null ? per : "todos") + ", " + filas.size() + " inmuebles");

        String nombre = "cartera-" + (per != null ? per : "total") + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"" + nombre + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(s.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------- RN-36: intereses de mora ----------

    @PostMapping("/intereses-mora")
    @Transactional
    public ResultadoIntereses generarIntereses(@RequestBody GenerarIntereses body, Authentication auth) {
        BigDecimal tasa = body.tasaMensual();
        if (tasa == null || tasa.signum() <= 0 || tasa.compareTo(TASA_MAXIMA) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La tasa mensual debe ser mayor que 0 y no pasar de 10 %");
        }
        LocalDate hoy = LocalDate.now();
        YearMonth mes = YearMonth.from(hoy);
        String periodo = mes.toString();
        LocalDate vence = mes.atEndOfMonth();

        List<Obligacion> todas = obligaciones.findAllByOrderByIdDesc();
        Set<String> existentes = new HashSet<>();
        for (Obligacion o : todas) {
            if (o.getConcepto() == ConceptoObligacion.INTERES_MORA && periodo.equals(o.getPeriodo())
                    && o.getEstado() != EstadoObligacion.ANULADA && o.getDescripcion() != null) {
                existentes.add(o.getDescripcion());
            }
        }

        int generados = 0;
        int yaExistian = 0;
        int omitidos = 0;
        long valorTotal = 0;
        for (Obligacion o : todas) {
            boolean base = o.getConcepto() == ConceptoObligacion.CUOTA_ADMINISTRACION
                    || o.getConcepto() == ConceptoObligacion.CUOTA_EXTRAORDINARIA;
            if (!base || !pendiente(o) || dias(o, hoy) <= 0) continue;

            String clave = "Mora de obligación #" + o.getId() + " ";
            boolean ya = false;
            for (String d : existentes) {
                if (d.startsWith(clave)) { ya = true; break; }
            }
            if (ya) { yaExistian++; continue; }

            long valor = BigDecimal.valueOf(o.getSaldo()).multiply(tasa)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
            if (valor <= 0) { omitidos++; continue; }

            Obligacion i = new Obligacion();
            i.setInmueble(o.getInmueble());
            i.setConcepto(ConceptoObligacion.INTERES_MORA);
            i.setPeriodo(periodo);
            i.setDescripcion(clave + "(" + o.getConcepto().getEtiqueta() + " " + o.getPeriodo() + ")");
            i.setValor(valor);
            i.setSaldo(valor);
            i.setFechaVencimiento(vence);
            i.setEstado(EstadoObligacion.PENDIENTE);
            i.setCreadaPor(auth.getName());
            obligaciones.save(i);
            generados++;
            valorTotal += valor;
        }

        auditoria.registrar("GENERAR_INTERESES", "Obligacion", 0L,
                "Periodo " + periodo + ", tasa " + tasa.stripTrailingZeros().toPlainString()
                        + " %, generados " + generados + ", valor " + valorTotal);
        return new ResultadoIntereses(periodo, generados, yaExistian, omitidos, valorTotal);
    }

    // ---------- RF-120 y RN-34: paz y salvo a una fecha de corte ----------

    @GetMapping("/inmuebles/{id}/paz-y-salvo")
    @Transactional
    public ResponseEntity<String> pazYSalvo(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate corte) {
        LocalDate hoy = LocalDate.now();
        LocalDate fecha = corte != null ? corte : hoy;
        if (fecha.isAfter(hoy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de corte no puede ser futura");
        }
        Inmueble inm = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));

        // Lo pagado hasta la fecha de corte por obligación.
        Map<Long, Long> pagado = new HashMap<>();
        for (Pago p : pagos.findByInmuebleIdInOrderByIdDesc(List.of(id))) {
            boolean vale = p.getEstado() == EstadoPago.CONFIRMADO
                    || (p.getEstado() == EstadoPago.ANULADO && p.getAnuladoEn() != null
                        && p.getAnuladoEn().toLocalDate().isAfter(fecha));
            if (!vale || p.getFecha().isAfter(fecha)) continue;
            for (PagoDetalle d : p.getDetalles()) {
                pagado.merge(d.getObligacion().getId(), d.getValor(), Long::sum);
            }
        }

        List<LineaDeuda> pendientes = new ArrayList<>();
        long total = 0;
        for (Obligacion o : obligaciones.findByInmuebleIdInOrderByIdDesc(List.of(id))) {
            if (o.getEstado() == EstadoObligacion.ANULADA) continue;
            if (o.getCreadaEn() != null && o.getCreadaEn().toLocalDate().isAfter(fecha)) continue;
            long debe = o.getValor() - pagado.getOrDefault(o.getId(), 0L);
            if (debe <= 0) continue;
            pendientes.add(new LineaDeuda(o.getId(), o.getConcepto().getEtiqueta(), o.getPeriodo(),
                    o.getDescripcion(), o.getValor(), debe, o.getFechaVencimiento(), dias(o, fecha)));
            total += debe;
        }
        pendientes.sort(Comparator.comparing(LineaDeuda::periodo).thenComparing(LineaDeuda::id));

        List<String> propietarios = new ArrayList<>();
        for (Vinculacion v : vinculaciones.findByInmuebleIdAndEstado(id, EstadoVinculacion.VIGENTE)) {
            if (v.getTipoVinculo() == TipoVinculo.PROPIETARIO_RESIDENTE
                    || v.getTipoVinculo() == TipoVinculo.PROPIETARIO_NO_RESIDENTE) {
                propietarios.add(v.getIntegrante().getNombres() + " " + v.getIntegrante().getApellidos());
            }
        }

        auditoria.registrar("EXPEDIR_PAZ_Y_SALVO", "Inmueble", id,
                "Corte " + fecha + ", " + (pendientes.isEmpty() ? "a paz y salvo" : "con saldo " + total));

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff")
                .body(htmlPazYSalvo(inm, fecha, hoy, pendientes, total, propietarios));
    }

    // ---------- Ayudas ----------

    private String periodo(String periodo) {
        if (periodo == null || periodo.isBlank()) return null;
        String p = periodo.trim();
        if (!PERIODO.matcher(p).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo debe tener el formato AAAA-MM");
        }
        return p;
    }

    private boolean pendiente(Obligacion o) {
        return o.getEstado() == EstadoObligacion.PENDIENTE && o.getSaldo() > 0;
    }

    private int dias(Obligacion o, LocalDate referencia) {
        LocalDate v = o.getFechaVencimiento();
        return v.isBefore(referencia) ? (int) ChronoUnit.DAYS.between(v, referencia) : 0;
    }

    // RN-25: la cartera se calcula por inmueble.
    private List<FilaCartera> filas(String periodo, Integer min, Integer max) {
        if ((min != null && min < 0) || (max != null && max < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los días de mora no pueden ser negativos");
        }
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mínimo de días no puede superar al máximo");
        }
        LocalDate hoy = LocalDate.now();
        Map<Long, List<Obligacion>> porInmueble = new LinkedHashMap<>();
        for (Obligacion o : obligaciones.findAllByOrderByIdDesc()) {
            if (!pendiente(o)) continue;
            if (periodo != null && !periodo.equals(o.getPeriodo())) continue;
            porInmueble.computeIfAbsent(o.getInmueble().getId(), k -> new ArrayList<>()).add(o);
        }
        List<FilaCartera> filas = new ArrayList<>();
        for (List<Obligacion> lista : porInmueble.values()) {
            Inmueble i = lista.get(0).getInmueble();
            long saldo = 0;
            long vencido = 0;
            int mora = 0;
            for (Obligacion o : lista) {
                saldo += o.getSaldo();
                int d = dias(o, hoy);
                if (d > 0) {
                    vencido += o.getSaldo();
                    mora = Math.max(mora, d);
                }
            }
            if (min != null && mora < min) continue;
            if (max != null && mora > max) continue;
            String tipo = i.getTipoInmueble() != null ? i.getTipoInmueble().getNombre() : "";
            filas.add(new FilaCartera(i.getId(), i.getIdentificacion(), tipo, saldo, vencido, lista.size(), mora));
        }
        filas.sort((a, b) -> Long.compare(b.saldoPendiente(), a.saldoPendiente()));
        return filas;
    }

    // Texto para CSV: entre comillas y sin fórmulas (evita que Excel ejecute =, +, - o @ al inicio).
    private static String csv(String s) {
        if (s == null) return "\"\"";
        String v = s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String h(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String htmlPazYSalvo(Inmueble inm, LocalDate corte, LocalDate hoy, List<LineaDeuda> pendientes,
                                 long total, List<String> propietarios) {
        boolean limpio = pendientes.isEmpty();
        NumberFormat pesos = NumberFormat.getInstance(Locale.forLanguageTag("es-CO"));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        StringBuilder s = new StringBuilder();
        s.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">");
        s.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        s.append("<title>Paz y salvo - ").append(h(inm.getIdentificacion())).append("</title>");
        s.append("<style>body{font-family:Arial,Helvetica,sans-serif;color:#1f2937;margin:0;padding:24px;background:#f3f4f6}");
        s.append(".hoja{max-width:680px;margin:0 auto;background:#fff;border:1px solid #d1d5db;border-radius:10px;padding:28px}");
        s.append("h1{font-size:1.4rem;margin:0 0 2px}.sub{color:#6b7280;margin:0 0 18px}");
        s.append("dl{display:grid;grid-template-columns:160px 1fr;gap:6px 12px;margin:0 0 18px}");
        s.append("dt{color:#6b7280}dd{margin:0}");
        s.append(".estado{border-radius:8px;padding:12px 16px;font-weight:700;font-size:1.1rem;margin:0 0 16px}");
        s.append(".ok{background:#e4f6ea;color:#14532d}.mal{background:#fde8e8;color:#7f1d1d}");
        s.append("table{width:100%;border-collapse:collapse;margin-bottom:10px}");
        s.append("th,td{padding:8px;border-bottom:1px solid #e5e7eb;text-align:left}");
        s.append(".n{text-align:right;white-space:nowrap}.total{font-size:1.1rem;font-weight:700;text-align:right}");
        s.append(".pie{color:#6b7280;font-size:.8rem;margin-top:18px}");
        s.append("button{margin-top:16px;padding:10px 18px;border:0;border-radius:8px;background:#2563eb;color:#fff;font-size:1rem;cursor:pointer}");
        s.append("@media print{body{background:#fff;padding:0}.hoja{border:0}button{display:none}}</style></head><body>");
        s.append("<div class=\"hoja\"><h1>Paz y salvo de expensas comunes</h1>");
        s.append("<p class=\"sub\">Administración de la copropiedad</p><dl>");
        s.append("<dt>Inmueble</dt><dd>").append(h(inm.getIdentificacion())).append("</dd>");
        s.append("<dt>Propietario</dt><dd>")
                .append(propietarios.isEmpty() ? "Sin propietario vinculado" : h(String.join(", ", propietarios)))
                .append("</dd>");
        s.append("<dt>Fecha de corte</dt><dd>").append(corte.format(fmt)).append("</dd>");
        s.append("<dt>Fecha de expedición</dt><dd>").append(hoy.format(fmt)).append("</dd></dl>");

        if (limpio) {
            s.append("<p class=\"estado ok\">A PAZ Y SALVO</p>");
            s.append("<p>A la fecha de corte, el inmueble no registra obligaciones pendientes con la copropiedad.</p>");
        } else {
            s.append("<p class=\"estado mal\">CON OBLIGACIONES PENDIENTES</p>");
            s.append("<p>A la fecha de corte, el inmueble registra las siguientes obligaciones sin pagar. ");
            s.append("No se expide paz y salvo mientras existan.</p>");
            s.append("<table><thead><tr><th>Concepto</th><th>Periodo</th><th>Vencimiento</th><th class=\"n\">Saldo</th></tr></thead><tbody>");
            for (LineaDeuda l : pendientes) {
                s.append("<tr><td>").append(h(l.concepto())).append("</td><td>").append(h(l.periodo()))
                        .append("</td><td>").append(l.fechaVencimiento().format(fmt))
                        .append("</td><td class=\"n\">$ ").append(pesos.format(l.saldo())).append("</td></tr>");
            }
            s.append("</tbody></table><p class=\"total\">Total pendiente: $ ").append(pesos.format(total)).append("</p>");
        }
        s.append("<p class=\"pie\">La transferencia del dominio exige este certificado y responde por las expensas no pagadas ");
        s.append("(artículo 29 de la Ley 675 de 2001). Documento generado por la plataforma de gestión de propiedad horizontal.</p>");
        s.append("<button onclick=\"window.print()\">Imprimir o guardar como PDF</button></div></body></html>");
        return s.toString();
    }
}
