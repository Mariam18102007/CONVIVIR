package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.HistorialInmuebleController.Historial;
import com.proyecto.plataforma_ph.model.CanalPago;
import com.proyecto.plataforma_ph.model.CategoriaReporte;
import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoPago;
import com.proyecto.plataforma_ph.model.EstadoReporte;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.MovimientoAcceso;
import com.proyecto.plataforma_ph.model.MovimientoVehicular;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.model.Reporte;
import com.proyecto.plataforma_ph.model.TipoVinculo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** RF-013: historial unificado de un inmueble. */
class HistorialInmuebleControllerTest {

    private InmuebleRepository inmuebles;
    private VinculacionRepository vinculaciones;
    private ObligacionRepository obligaciones;
    private PagoRepository pagos;
    private ReporteRepository reportes;
    private MovimientoAccesoRepository accesosPersonas;
    private MovimientoVehicularRepository accesosVehiculos;
    private HistorialInmuebleController controller;
    private Inmueble inmueble;

    @BeforeEach
    void preparar() {
        inmuebles = mock(InmuebleRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        obligaciones = mock(ObligacionRepository.class);
        pagos = mock(PagoRepository.class);
        reportes = mock(ReporteRepository.class);
        accesosPersonas = mock(MovimientoAccesoRepository.class);
        accesosVehiculos = mock(MovimientoVehicularRepository.class);
        controller = new HistorialInmuebleController(inmuebles, vinculaciones, obligaciones,
                pagos, reportes, accesosPersonas, accesosVehiculos);

        inmueble = new Inmueble();
        ReflectionTestUtils.setField(inmueble, "id", 10L);
        inmueble.setIdentificacion("Apto 101");
        when(inmuebles.findById(10L)).thenReturn(Optional.of(inmueble));

        when(vinculaciones.findByInmuebleIdOrderByFechaInicioDescIdDesc(10L)).thenReturn(List.of());
        when(obligaciones.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of());
        when(pagos.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of());
        when(reportes.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of());
        when(accesosPersonas.findByInmuebleIdOrderByIngresoEnDesc(10L)).thenReturn(List.of());
        when(accesosVehiculos.findByInmuebleIdOrderByIngresoEnDesc(10L)).thenReturn(List.of());
    }

    private MovimientoAcceso persona(String nombre, LocalDateTime ingreso) {
        MovimientoAcceso m = new MovimientoAcceso();
        m.setNombre(nombre);
        m.setIngresoEn(ingreso);
        return m;
    }

    private MovimientoVehicular vehiculo(String placa, LocalDateTime ingreso) {
        MovimientoVehicular m = new MovimientoVehicular();
        m.setPlaca(placa);
        m.setIngresoEn(ingreso);
        return m;
    }

    @Test
    void inmuebleInexistenteDevuelveNoEncontrado() {
        when(inmuebles.findById(99L)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.historial(99L));
        assertEquals(HttpStatus.NOT_FOUND, HttpStatus.valueOf(e.getStatusCode().value()));
    }

    @Test
    void sinMovimientosDevuelveTodasLasSeccionesVacias() {
        Historial h = controller.historial(10L);
        assertEquals(10L, h.inmuebleId());
        assertEquals("Apto 101", h.identificacion());
        assertTrue(h.vinculaciones().isEmpty());
        assertTrue(h.obligaciones().isEmpty());
        assertTrue(h.pagos().isEmpty());
        assertTrue(h.reportes().isEmpty());
        assertTrue(h.accesos().isEmpty());
    }

    @Test
    void incluyeVinculacionesFinalizadasComoHistorial() {
        Integrante ana = new Integrante();
        ana.setNombres("Ana");
        ana.setApellidos("Pérez");
        Vinculacion v = new Vinculacion();
        ReflectionTestUtils.setField(v, "id", 3L);
        v.setIntegrante(ana);
        v.setInmueble(inmueble);
        v.setTipoVinculo(TipoVinculo.ARRENDATARIO);
        v.setFechaInicio(LocalDate.of(2025, 1, 1));
        v.setFechaFin(LocalDate.of(2025, 12, 31));
        v.setEstado(EstadoVinculacion.FINALIZADA);
        when(vinculaciones.findByInmuebleIdOrderByFechaInicioDescIdDesc(10L)).thenReturn(List.of(v));

        var fila = controller.historial(10L).vinculaciones().get(0);

        assertEquals("Ana Pérez", fila.integrante());
        assertEquals("ARRENDATARIO", fila.tipoVinculo());
        assertEquals("FINALIZADA", fila.estado());
        assertEquals(LocalDate.of(2025, 12, 31), fila.fechaFin());
    }

    @Test
    void obligacionesPagosYReportesSeMapeanConSusValores() {
        Obligacion o = new Obligacion();
        ReflectionTestUtils.setField(o, "id", 1L);
        o.setInmueble(inmueble);
        ReflectionTestUtils.setField(o, "concepto", ConceptoObligacion.MULTA);
        ReflectionTestUtils.setField(o, "periodo", "2026-10");
        ReflectionTestUtils.setField(o, "valor", 150000L);
        ReflectionTestUtils.setField(o, "saldo", 50000L);
        when(obligaciones.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(o));

        Pago p = new Pago();
        ReflectionTestUtils.setField(p, "id", 4L);
        p.setInmueble(inmueble);
        ReflectionTestUtils.setField(p, "canal", CanalPago.PRESENCIAL);
        ReflectionTestUtils.setField(p, "estado", EstadoPago.CONFIRMADO);
        ReflectionTestUtils.setField(p, "pagador", "Pedro");
        PagoDetalle d1 = new PagoDetalle();
        ReflectionTestUtils.setField(d1, "valor", 60000L);
        PagoDetalle d2 = new PagoDetalle();
        ReflectionTestUtils.setField(d2, "valor", 40000L);
        ReflectionTestUtils.setField(p, "detalles", new ArrayList<>(List.of(d1, d2)));
        when(pagos.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(p));

        CategoriaReporte cat = new CategoriaReporte();
        cat.setNombre("Daños");
        Reporte r = new Reporte();
        ReflectionTestUtils.setField(r, "id", 8L);
        ReflectionTestUtils.setField(r, "categoria", cat);
        ReflectionTestUtils.setField(r, "descripcion", "Fuga en el baño");
        ReflectionTestUtils.setField(r, "estado", EstadoReporte.ABIERTO);
        when(reportes.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(r));

        Historial h = controller.historial(10L);

        assertEquals("MULTA", h.obligaciones().get(0).concepto());
        assertEquals(150000L, h.obligaciones().get(0).valor());
        assertEquals(50000L, h.obligaciones().get(0).saldo());
        assertEquals(100000L, h.pagos().get(0).valor());
        assertEquals("PRESENCIAL", h.pagos().get(0).canal());
        assertEquals("Daños", h.reportes().get(0).categoria());
        assertEquals("ABIERTO", h.reportes().get(0).estado());
    }

    @Test
    void accesosDePersonasYVehiculosVanEnUnaSolaLineaDeTiempoOrdenada() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 8, 0);
        when(accesosPersonas.findByInmuebleIdOrderByIngresoEnDesc(10L)).thenReturn(List.of(
                persona("Visita 2", base.plusHours(5)), persona("Visita 1", base)));
        when(accesosVehiculos.findByInmuebleIdOrderByIngresoEnDesc(10L)).thenReturn(List.of(
                vehiculo("ABC123", base.plusHours(2))));

        var accesos = controller.historial(10L).accesos();

        assertEquals(3, accesos.size());
        assertEquals("Visita 2", accesos.get(0).nombre());
        assertEquals("ABC123", accesos.get(1).nombre());
        assertEquals("VEHICULO", accesos.get(1).tipo());
        assertEquals("Visita 1", accesos.get(2).nombre());
        assertEquals("PERSONA", accesos.get(2).tipo());
    }

    @Test
    void limitaCadaSeccionAlMaximoDefinido() {
        List<MovimientoAcceso> muchos = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (int i = 0; i < HistorialInmuebleController.MAXIMO_POR_SECCION + 50; i++) {
            muchos.add(persona("P" + i, base.plusMinutes(i)));
        }
        when(accesosPersonas.findByInmuebleIdOrderByIngresoEnDesc(10L)).thenReturn(muchos);

        assertEquals(HistorialInmuebleController.MAXIMO_POR_SECCION, controller.historial(10L).accesos().size());
    }
}
