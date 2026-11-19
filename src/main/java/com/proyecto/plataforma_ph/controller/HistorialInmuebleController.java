package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.MovimientoAcceso;
import com.proyecto.plataforma_ph.model.MovimientoVehicular;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.model.Reporte;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.MovimientoAccesoRepository;
import com.proyecto.plataforma_ph.repository.MovimientoVehicularRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.PagoRepository;
import com.proyecto.plataforma_ph.repository.ReporteRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-013: el administrador consulta el historial de un inmueble, que reúne vinculaciones,
 * obligaciones, pagos, reportes y movimientos de acceso. Solo lectura (RN-01, RN-04).
 * Cada lista trae los registros más recientes primero, hasta un máximo por sección.
 */
@RestController
@RequestMapping("/api/inmuebles")
public class HistorialInmuebleController {

    static final int MAXIMO_POR_SECCION = 200;

    public record VinculacionFila(Long id, String integrante, String tipoVinculo,
                                  LocalDate fechaInicio, LocalDate fechaFin, String estado) {}

    public record ObligacionFila(Long id, String concepto, String periodo, long valor, long saldo,
                                 LocalDate fechaVencimiento, String estado) {}

    public record PagoFila(Long id, String canal, String medio, String estado,
                           LocalDate fecha, String pagador, long valor) {}

    public record ReporteFila(Long id, String categoria, String descripcion, String estado,
                              LocalDateTime creadoEn, LocalDateTime cerradoEn) {}

    public record AccesoFila(String tipo, String nombre, String detalle,
                             LocalDateTime ingresoEn, LocalDateTime salidaEn) {}

    public record Historial(Long inmuebleId, String identificacion,
                            List<VinculacionFila> vinculaciones, List<ObligacionFila> obligaciones,
                            List<PagoFila> pagos, List<ReporteFila> reportes, List<AccesoFila> accesos) {}

    private final InmuebleRepository inmuebles;
    private final VinculacionRepository vinculaciones;
    private final ObligacionRepository obligaciones;
    private final PagoRepository pagos;
    private final ReporteRepository reportes;
    private final MovimientoAccesoRepository accesosPersonas;
    private final MovimientoVehicularRepository accesosVehiculos;

    public HistorialInmuebleController(InmuebleRepository inmuebles,
                                       VinculacionRepository vinculaciones,
                                       ObligacionRepository obligaciones,
                                       PagoRepository pagos,
                                       ReporteRepository reportes,
                                       MovimientoAccesoRepository accesosPersonas,
                                       MovimientoVehicularRepository accesosVehiculos) {
        this.inmuebles = inmuebles;
        this.vinculaciones = vinculaciones;
        this.obligaciones = obligaciones;
        this.pagos = pagos;
        this.reportes = reportes;
        this.accesosPersonas = accesosPersonas;
        this.accesosVehiculos = accesosVehiculos;
    }

    @GetMapping("/{id}/historial")
    @Transactional(readOnly = true)
    public Historial historial(@PathVariable Long id) {
        Inmueble inmueble = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));

        List<VinculacionFila> filasVinculaciones = vinculaciones
                .findByInmuebleIdOrderByFechaInicioDescIdDesc(id).stream()
                .limit(MAXIMO_POR_SECCION).map(this::fila).toList();

        List<ObligacionFila> filasObligaciones = obligaciones
                .findByInmuebleIdOrderByIdDesc(id).stream()
                .limit(MAXIMO_POR_SECCION).map(this::fila).toList();

        List<PagoFila> filasPagos = pagos
                .findByInmuebleIdOrderByIdDesc(id).stream()
                .limit(MAXIMO_POR_SECCION).map(this::fila).toList();

        List<ReporteFila> filasReportes = reportes
                .findByInmuebleIdOrderByIdDesc(id).stream()
                .limit(MAXIMO_POR_SECCION).map(this::fila).toList();

        // Personas y vehículos en una sola línea de tiempo, de lo más reciente a lo más antiguo.
        List<AccesoFila> filasAccesos = java.util.stream.Stream.concat(
                        accesosPersonas.findByInmuebleIdOrderByIngresoEnDesc(id).stream().map(this::fila),
                        accesosVehiculos.findByInmuebleIdOrderByIngresoEnDesc(id).stream().map(this::fila))
                .sorted(java.util.Comparator.comparing(AccesoFila::ingresoEn,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(MAXIMO_POR_SECCION)
                .toList();

        return new Historial(inmueble.getId(), inmueble.getIdentificacion(),
                filasVinculaciones, filasObligaciones, filasPagos, filasReportes, filasAccesos);
    }

    private VinculacionFila fila(Vinculacion v) {
        String nombre = v.getIntegrante().getNombres() + " " + v.getIntegrante().getApellidos();
        return new VinculacionFila(v.getId(), nombre, v.getTipoVinculo().name(),
                v.getFechaInicio(), v.getFechaFin(), v.getEstado().name());
    }

    private ObligacionFila fila(Obligacion o) {
        return new ObligacionFila(o.getId(), o.getConcepto().name(), o.getPeriodo(), o.getValor(),
                o.getSaldo(), o.getFechaVencimiento(), o.getEstado().name());
    }

    private PagoFila fila(Pago p) {
        long total = p.getDetalles().stream().mapToLong(PagoDetalle::getValor).sum();
        return new PagoFila(p.getId(), p.getCanal().name(),
                p.getMedio() == null ? null : p.getMedio().name(),
                p.getEstado().name(), p.getFecha(), p.getPagador(), total);
    }

    private ReporteFila fila(Reporte r) {
        return new ReporteFila(r.getId(), r.getCategoria().getNombre(), r.getDescripcion(),
                r.getEstado().name(), r.getCreadoEn(), r.getCerradoEn());
    }

    private AccesoFila fila(MovimientoAcceso m) {
        String detalle = m.getMotivo() != null && !m.getMotivo().isBlank() ? m.getMotivo() : m.getPlaca();
        return new AccesoFila("PERSONA", m.getNombre(), detalle, m.getIngresoEn(), m.getSalidaEn());
    }

    private AccesoFila fila(MovimientoVehicular m) {
        String detalle = m.getParqueadero() == null ? null : m.getParqueadero().getIdentificacion();
        return new AccesoFila("VEHICULO", m.getPlaca(), detalle, m.getIngresoEn(), m.getSalidaEn());
    }
}
