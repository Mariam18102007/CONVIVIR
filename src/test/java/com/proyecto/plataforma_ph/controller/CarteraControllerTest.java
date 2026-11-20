package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.CarteraController.DetalleCartera;
import com.proyecto.plataforma_ph.controller.CarteraController.FilaCartera;
import com.proyecto.plataforma_ph.controller.CarteraController.GenerarIntereses;
import com.proyecto.plataforma_ph.controller.CarteraController.ResultadoIntereses;
import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoPago;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.model.TipoInmueble;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.PagoRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cartera (CU-14, RN-25): listado y filtros por mora (RF-083, RF-085), detalle por inmueble (RF-084),
 * exportación (RF-086), intereses de mora (RN-36) y paz y salvo a una fecha de corte (RF-120, RN-34).
 */
class CarteraControllerTest {

    private ObligacionRepository obligaciones;
    private InmuebleRepository inmuebles;
    private PagoRepository pagos;
    private VinculacionRepository vinculaciones;
    private AuditoriaService auditoria;
    private CarteraController controller;
    private Authentication auth;

    private Inmueble apto101;
    private Inmueble apto202;
    private Inmueble local1;

    @BeforeEach
    void preparar() {
        obligaciones = mock(ObligacionRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        pagos = mock(PagoRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        auditoria = mock(AuditoriaService.class);
        controller = new CarteraController(obligaciones, inmuebles, pagos, vinculaciones, auditoria);

        apto101 = inmueble(10L, "Apto 101", "Residencial");
        apto202 = inmueble(20L, "Apto 202", null);
        local1 = inmueble(30L, "Local 1", "Comercial");
        when(inmuebles.findById(10L)).thenReturn(Optional.of(apto101));
        when(inmuebles.findById(20L)).thenReturn(Optional.of(apto202));
        when(inmuebles.findById(30L)).thenReturn(Optional.of(local1));

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@ph.local");
    }

    // ---------- Listado (RF-083, RF-085, RN-25) ----------

    /** Apto 101: 150.000 (100.000 vencido hace 40 días); Apto 202: 300.000 vencido hace 5 días; Local 1: 20.000 al día. */
    private void cargarCartera() {
        Obligacion o1 = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 40);
        Obligacion o2 = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L, -10);
        Obligacion o3 = obligacion(3L, apto202, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 300_000L, 5);
        Obligacion o4 = obligacion(4L, local1, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 20_000L, -5);
        // No cuentan: anulada, pagada y pendiente sin saldo.
        Obligacion anulada = obligacion(5L, apto202, ConceptoObligacion.MULTA, "2026-11", 70_000L, 30);
        anulada.setEstado(EstadoObligacion.ANULADA);
        Obligacion pagada = obligacion(6L, apto202, ConceptoObligacion.MULTA, "2026-11", 70_000L, 30);
        pagada.setEstado(EstadoObligacion.PAGADA);
        pagada.setSaldo(0);
        Obligacion sinSaldo = obligacion(7L, apto202, ConceptoObligacion.MULTA, "2026-11", 70_000L, 30);
        sinSaldo.setSaldo(0);
        when(obligaciones.findAllByOrderByIdDesc())
                .thenReturn(List.of(sinSaldo, pagada, anulada, o4, o3, o2, o1));
    }

    @Test
    void listarAgrupaPorInmuebleYOrdenaPorSaldoDescendente() {
        cargarCartera();

        List<FilaCartera> filas = controller.listar(null, null, null);

        assertEquals(3, filas.size());
        assertEquals(List.of(20L, 10L, 30L), filas.stream().map(FilaCartera::inmuebleId).toList());

        FilaCartera a101 = filas.get(1);
        assertEquals("Apto 101", a101.inmueble());
        assertEquals("Residencial", a101.tipo());
        assertEquals(150_000L, a101.saldoPendiente());
        assertEquals(100_000L, a101.saldoVencido());
        assertEquals(2, a101.obligaciones());
        assertEquals(40, a101.diasMora());

        FilaCartera a202 = filas.get(0);
        assertEquals(300_000L, a202.saldoPendiente());
        assertEquals(1, a202.obligaciones());
        assertEquals(5, a202.diasMora());
        assertEquals("", a202.tipo()); // sin tipo de inmueble

        assertEquals(0, filas.get(2).diasMora());
        assertEquals(0L, filas.get(2).saldoVencido());
    }

    @Test
    void listarUsaComoMoraLosDiasDeLaObligacionMasAtrasada() {
        Obligacion reciente = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 10_000L, 3);
        Obligacion antigua = obligacion(2L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-08", 10_000L, 90);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(reciente, antigua));

        assertEquals(90, controller.listar(null, null, null).get(0).diasMora());
    }

    @Test
    void listarFiltraPorPeriodoYAceptaEspaciosOVacio() {
        cargarCartera();

        List<FilaCartera> filas = controller.listar("2026-11", null, null);

        // Apto 101 solo cuenta su multa de noviembre (50.000), sin la cuota de octubre.
        assertEquals(List.of(20L, 10L, 30L), filas.stream().map(FilaCartera::inmuebleId).toList());
        assertEquals(50_000L, filas.get(1).saldoPendiente());

        assertEquals(1, controller.listar(" 2026-10 ", null, null).size());
        assertEquals(3, controller.listar("  ", null, null).size());
        assertEquals(0, controller.listar("2027-01", null, null).size());
    }

    @Test
    void listarFiltraPorRangoDeDiasDeMora() {
        cargarCartera();

        assertEquals(List.of(10L), controller.listar(null, 10, null).stream().map(FilaCartera::inmuebleId).toList());
        assertEquals(List.of(20L, 30L), controller.listar(null, null, 10).stream().map(FilaCartera::inmuebleId).toList());
        assertEquals(List.of(20L), controller.listar(null, 5, 5).stream().map(FilaCartera::inmuebleId).toList());
        // RF-085: al día (0 días de mora).
        assertEquals(List.of(30L), controller.listar(null, 0, 0).stream().map(FilaCartera::inmuebleId).toList());
        assertEquals(3, controller.listar(null, 0, null).size());
    }

    @Test
    void listarRechazaFiltrosInvalidos() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.listar("2026-13", null, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.listar("noviembre", null, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.listar(null, -1, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.listar(null, null, -1));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.listar(null, 30, 10));
    }

    @Test
    void listarSinObligacionesDevuelveListaVacia() {
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of());

        assertTrue(controller.listar(null, null, null).isEmpty());
    }

    // ---------- Detalle (RF-084) ----------

    @Test
    void detalleListaLasDeudasPendientesOrdenadasPorPeriodoYSumaSaldos() {
        Obligacion nov = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L, -5);
        Obligacion oct = obligacion(2L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion sep = obligacion(5L, apto101, ConceptoObligacion.MULTA, "2026-09", 50_000L, -3);
        sep.setDescripcion("Ruido");
        Obligacion anulada = obligacion(6L, apto101, ConceptoObligacion.MULTA, "2026-09", 9_000L, 20);
        anulada.setEstado(EstadoObligacion.ANULADA);
        Obligacion pagada = obligacion(7L, apto101, ConceptoObligacion.MULTA, "2026-09", 9_000L, 20);
        pagada.setEstado(EstadoObligacion.PAGADA);
        pagada.setSaldo(0);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any()))
                .thenReturn(List.of(pagada, anulada, nov, oct, sep));

        DetalleCartera d = controller.detalle(10L, null);

        assertEquals("Apto 101", d.inmueble());
        assertEquals(List.of(5L, 2L, 1L), d.deudas().stream().map(l -> l.id()).toList());
        assertEquals("Multa", d.deudas().get(0).concepto());
        assertEquals("Ruido", d.deudas().get(0).descripcion());
        assertEquals("Cuota de administración", d.deudas().get(1).concepto());
        assertEquals(10, d.deudas().get(1).diasMora());
        assertEquals(250_000L, d.saldoPendiente());
        assertEquals(100_000L, d.saldoVencido());
    }

    @Test
    void detalleFiltraPorPeriodo() {
        Obligacion nov = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L, 2);
        Obligacion oct = obligacion(2L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 80_000L, 32);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(nov, oct));

        DetalleCartera d = controller.detalle(10L, "2026-10");

        assertEquals(1, d.deudas().size());
        assertEquals(80_000L, d.saldoPendiente());
    }

    @Test
    void detalleRechazaInmuebleInexistenteOPeriodoInvalido() {
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.detalle(999L, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.detalle(10L, "2026/11"));
    }

    @Test
    void detalleDeUnInmuebleSinDeudaVieneVacio() {
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of());

        DetalleCartera d = controller.detalle(10L, null);

        assertTrue(d.deudas().isEmpty());
        assertEquals(0L, d.saldoPendiente());
        assertEquals(0L, d.saldoVencido());
    }

    // ---------- Exportar (RF-086) ----------

    @Test
    void exportarGeneraUnCsvConEncabezadoFilasYTotal() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L, 4);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(o));

        ResponseEntity<byte[]> r = controller.exportar("2026-11", null, null);

        String csv = new String(r.getBody(), StandardCharsets.UTF_8);
        assertEquals(200, r.getStatusCode().value());
        assertTrue(csv.startsWith("﻿"));
        assertTrue(csv.contains("Inmueble;Tipo;Saldo pendiente;Saldo vencido;Obligaciones pendientes;Dias de mora\r\n"));
        assertTrue(csv.contains("\"Apto 101\";\"Residencial\";100000;100000;1;4\r\n"));
        assertTrue(csv.endsWith("TOTAL;;100000;100000;;\r\n"));
        assertEquals("attachment; filename=\"cartera-2026-11.csv\"", r.getHeaders().getFirst("Content-Disposition"));
        assertEquals("nosniff", r.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("csv", r.getHeaders().getContentType().getSubtype());
        verify(auditoria).registrar(eq("EXPORTAR_CARTERA"), eq("Cartera"), eq(0L), contains("Periodo 2026-11"));
    }

    @Test
    void exportarSinPeriodoUsaNombreTotalYSumaTodasLasFilas() {
        cargarCartera();

        ResponseEntity<byte[]> r = controller.exportar(null, null, null);

        String csv = new String(r.getBody(), StandardCharsets.UTF_8);
        assertEquals("attachment; filename=\"cartera-total.csv\"", r.getHeaders().getFirst("Content-Disposition"));
        assertTrue(csv.endsWith("TOTAL;;470000;400000;;\r\n"));
        verify(auditoria).registrar(eq("EXPORTAR_CARTERA"), eq("Cartera"), eq(0L), contains("Periodo todos"));
    }

    @Test
    void exportarSinDatosSoloTraeEncabezadoYTotalEnCero() {
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of());

        String csv = new String(controller.exportar(null, null, null).getBody(), StandardCharsets.UTF_8);

        assertTrue(csv.endsWith("Dias de mora\r\nTOTAL;;0;0;;\r\n"));
    }

    @Test
    void exportarNeutralizaFormulasYEscapaComillas() {
        Inmueble peligroso = inmueble(40L, "=HYPERLINK(\"http://x\")", "Casa \"A\"");
        Inmueble arroba = inmueble(41L, "@cmd", "+tipo");
        Obligacion a = obligacion(1L, peligroso, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 1_000L, 1);
        Obligacion b = obligacion(2L, arroba, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 900L, 1);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(a, b));

        String csv = new String(controller.exportar(null, null, null).getBody(), StandardCharsets.UTF_8);

        // Una fórmula al inicio recibe una comilla simple, y las comillas internas se duplican.
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://x\"\")\";\"Casa \"\"A\"\"\";1000;1000;1;1\r\n"));
        assertTrue(csv.contains("\"'@cmd\";\"'+tipo\";900;900;1;1\r\n"));
    }

    @Test
    void exportarRechazaFiltrosInvalidos() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.exportar("2026-99", null, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.exportar(null, 10, 5));
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    // ---------- Intereses de mora (RN-36) ----------

    @Test
    void generarInteresesRechazaTasasFueraDeRango() {
        for (String t : new String[] {"0", "-1", "10.01", "100"}) {
            assertEstado(HttpStatus.BAD_REQUEST,
                    () -> controller.generarIntereses(new GenerarIntereses(new BigDecimal(t)), auth));
        }
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.generarIntereses(new GenerarIntereses(null), auth));
        verify(obligaciones, never()).save(any());
    }

    @Test
    void generarInteresesAceptaLaTasaMaxima() {
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of());

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("10")), auth);

        assertEquals(0, r.generados());
    }

    @Test
    void generarInteresesSoloSobreCuotasVencidasConSaldo() {
        Obligacion cuota = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion extra = obligacion(2L, apto202, ConceptoObligacion.CUOTA_EXTRAORDINARIA, "2026-09", 80_000L, 3);
        extra.setSaldo(50_000L); // el interés se calcula sobre el saldo, no sobre el valor original
        // No generan interés:
        Obligacion multa = obligacion(3L, apto101, ConceptoObligacion.MULTA, "2026-10", 90_000L, 10);
        Obligacion mora = obligacion(4L, apto101, ConceptoObligacion.INTERES_MORA, "2026-10", 90_000L, 10);
        Obligacion zona = obligacion(5L, apto101, ConceptoObligacion.ALQUILER_ZONA, "2026-10", 90_000L, 10);
        Obligacion alDia = obligacion(6L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 90_000L, -5);
        Obligacion anulada = obligacion(7L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-08", 90_000L, 60);
        anulada.setEstado(EstadoObligacion.ANULADA);
        Obligacion pagada = obligacion(8L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-07", 90_000L, 90);
        pagada.setEstado(EstadoObligacion.PAGADA);
        pagada.setSaldo(0);
        when(obligaciones.findAllByOrderByIdDesc())
                .thenReturn(List.of(pagada, anulada, alDia, zona, mora, multa, extra, cuota));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("2")), auth);

        YearMonth mes = YearMonth.now();
        assertEquals(mes.toString(), r.periodo());
        assertEquals(2, r.generados());
        assertEquals(0, r.yaExistian());
        assertEquals(0, r.omitidos());
        assertEquals(3_000L, r.valorTotal()); // 2 % de 100.000 + 2 % de 50.000

        ArgumentCaptor<Obligacion> captor = ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones, times(2)).save(captor.capture());
        Obligacion deCuota = captor.getAllValues().stream()
                .filter(o -> o.getInmueble() == apto101).findFirst().orElseThrow();
        assertEquals(ConceptoObligacion.INTERES_MORA, deCuota.getConcepto());
        assertEquals(2_000L, deCuota.getValor());
        assertEquals(2_000L, deCuota.getSaldo());
        assertEquals(mes.toString(), deCuota.getPeriodo());
        assertEquals(mes.atEndOfMonth(), deCuota.getFechaVencimiento());
        assertEquals(EstadoObligacion.PENDIENTE, deCuota.getEstado());
        assertEquals("admin@ph.local", deCuota.getCreadaPor());
        assertEquals("Mora de obligación #1 (Cuota de administración 2026-10)", deCuota.getDescripcion());

        Obligacion deExtra = captor.getAllValues().stream()
                .filter(o -> o.getInmueble() == apto202).findFirst().orElseThrow();
        assertEquals(1_000L, deExtra.getValor());
        verify(auditoria).registrar(eq("GENERAR_INTERESES"), eq("Obligacion"), eq(0L), contains("generados 2"));
    }

    @Test
    void generarInteresesNoDuplicaElInteresYaGeneradoEnElMes() {
        Obligacion cuota = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion interes = obligacion(9L, apto101, ConceptoObligacion.INTERES_MORA, YearMonth.now().toString(), 2_000L, -1);
        interes.setDescripcion("Mora de obligación #1 (Cuota de administración 2026-10)");
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(interes, cuota));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("2")), auth);

        assertEquals(0, r.generados());
        assertEquals(1, r.yaExistian());
        verify(obligaciones, never()).save(any());
    }

    @Test
    void generarInteresesVuelveAGenerarSiElInteresAnteriorFueAnulado() {
        Obligacion cuota = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion interes = obligacion(9L, apto101, ConceptoObligacion.INTERES_MORA, YearMonth.now().toString(), 2_000L, -1);
        interes.setDescripcion("Mora de obligación #1 (Cuota de administración 2026-10)");
        interes.setEstado(EstadoObligacion.ANULADA);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(interes, cuota));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("2")), auth);

        assertEquals(1, r.generados());
        assertEquals(0, r.yaExistian());
    }

    @Test
    void generarInteresesNoConfundeLaObligacion1ConLaObligacion10() {
        Obligacion cuota10 = obligacion(10L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion interesDeLa1 = obligacion(9L, apto101, ConceptoObligacion.INTERES_MORA, YearMonth.now().toString(), 2_000L, -1);
        interesDeLa1.setDescripcion("Mora de obligación #1 (Cuota de administración 2026-09)");
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(interesDeLa1, cuota10));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("2")), auth);

        assertEquals(1, r.generados());
        assertEquals(0, r.yaExistian());
    }

    @Test
    void generarInteresesOmiteLosQueSeRedondeanACero() {
        Obligacion diminuta = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 10L, 10);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(diminuta));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("1")), auth);

        assertEquals(0, r.generados());
        assertEquals(1, r.omitidos());
        verify(obligaciones, never()).save(any());
    }

    @Test
    void generarInteresesRedondeaAlPesoMasCercano() {
        // 150 x 1 % = 1,5 -> 2
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 150L, 10);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(o));

        ResultadoIntereses r = controller.generarIntereses(new GenerarIntereses(new BigDecimal("1")), auth);

        assertEquals(2L, r.valorTotal());
    }

    // ---------- Paz y salvo (RF-120, RN-34) ----------

    @Test
    void pazYSalvoRechazaFechaDeCorteFuturaEInmuebleInexistente() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.pazYSalvo(10L, LocalDate.now().plusDays(1)));
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.pazYSalvo(999L, null));
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    @Test
    void pazYSalvoSinDeudaSeExpideYSeAudita() {
        ResponseEntity<String> r = controller.pazYSalvo(10L, null);

        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getBody().contains("A PAZ Y SALVO"));
        assertFalse(r.getBody().contains("CON OBLIGACIONES PENDIENTES"));
        assertTrue(r.getBody().contains("Apto 101"));
        assertEquals("html", r.getHeaders().getContentType().getSubtype());
        assertEquals("nosniff", r.getHeaders().getFirst("X-Content-Type-Options"));
        verify(auditoria).registrar(eq("EXPEDIR_PAZ_Y_SALVO"), eq("Inmueble"), eq(10L), contains("a paz y salvo"));
    }

    @Test
    void pazYSalvoConDeudaNoSeExpideYMuestraElTotal() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        Obligacion o2 = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L, -5);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o2, o));

        String html = controller.pazYSalvo(10L, null).getBody();

        assertTrue(html.contains("CON OBLIGACIONES PENDIENTES"));
        assertFalse(html.contains("A PAZ Y SALVO"));
        assertTrue(html.contains("Cuota de administración"));
        assertTrue(html.contains("Multa"));
        assertTrue(html.contains("Total pendiente"));
        verify(auditoria).registrar(eq("EXPEDIR_PAZ_Y_SALVO"), eq("Inmueble"), eq(10L), contains("con saldo 150000"));
    }

    @Test
    void pazYSalvoDescuentaLosPagosConfirmadosHastaElCorte() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o));
        Pago pago = pago(EstadoPago.CONFIRMADO, LocalDate.now().minusDays(2), null, o, 100_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(pago));

        assertTrue(controller.pazYSalvo(10L, null).getBody().contains("A PAZ Y SALVO"));
    }

    @Test
    void pazYSalvoUnAbonoParcialDejaElRestoPendiente() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o));
        Pago abono = pago(EstadoPago.CONFIRMADO, LocalDate.now().minusDays(2), null, o, 40_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(abono));

        controller.pazYSalvo(10L, null);

        verify(auditoria).registrar(eq("EXPEDIR_PAZ_Y_SALVO"), eq("Inmueble"), eq(10L), contains("con saldo 60000"));
    }

    @Test
    void pazYSalvoIgnoraPagosPosterioresAlCorte() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 40);
        ReflectionTestUtils.setField(o, "creadaEn", LocalDateTime.now().minusDays(30));
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o));
        Pago despues = pago(EstadoPago.CONFIRMADO, LocalDate.now().minusDays(2), null, o, 100_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(despues));

        String html = controller.pazYSalvo(10L, LocalDate.now().minusDays(5)).getBody();

        assertTrue(html.contains("CON OBLIGACIONES PENDIENTES"));
    }

    @Test
    void pazYSalvoIgnoraLasObligacionesCreadasDespuesDelCorteYLasAnuladas() {
        Obligacion nueva = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L, -5);
        Obligacion anulada = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-09", 50_000L, 30);
        ReflectionTestUtils.setField(anulada, "creadaEn", LocalDateTime.now().minusDays(60));
        anulada.setEstado(EstadoObligacion.ANULADA);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(nueva, anulada));

        // 'nueva' se creó hoy, después del corte de hace 5 días.
        String html = controller.pazYSalvo(10L, LocalDate.now().minusDays(5)).getBody();

        assertTrue(html.contains("A PAZ Y SALVO"));
    }

    @Test
    void pazYSalvoUnPagoAnuladoDespuesDelCorteAunCuentaYUnoAnuladoAntesNo() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 40);
        ReflectionTestUtils.setField(o, "creadaEn", LocalDateTime.now().minusDays(30));
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o));
        LocalDate corte = LocalDate.now().minusDays(5);

        // Pagado hace 10 días y anulado ayer: al corte seguía vigente.
        Pago anuladoDespues = pago(EstadoPago.ANULADO, LocalDate.now().minusDays(10),
                LocalDateTime.now().minusDays(1), o, 100_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(anuladoDespues));
        assertTrue(controller.pazYSalvo(10L, corte).getBody().contains("A PAZ Y SALVO"));

        // Anulado hace 7 días: ya estaba anulado al corte.
        Pago anuladoAntes = pago(EstadoPago.ANULADO, LocalDate.now().minusDays(10),
                LocalDateTime.now().minusDays(7), o, 100_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(anuladoAntes));
        assertTrue(controller.pazYSalvo(10L, corte).getBody().contains("CON OBLIGACIONES PENDIENTES"));
    }

    @Test
    void pazYSalvoNoCuentaPagosPendientesDeConfirmar() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L, 10);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(o));
        Pago sinConfirmar = pago(EstadoPago.PENDIENTE, LocalDate.now(), null, o, 100_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(sinConfirmar));

        assertTrue(controller.pazYSalvo(10L, null).getBody().contains("CON OBLIGACIONES PENDIENTES"));
    }

    @Test
    void pazYSalvoMuestraSoloLosPropietariosVigentesYEscapaElHtml() {
        Vinculacion propietario = vinculacion(TipoVinculo.PROPIETARIO_RESIDENTE, "Ana", "<script>alert(1)</script>");
        Vinculacion arrendatario = vinculacion(TipoVinculo.ARRENDATARIO, "Luis", "Inquilino");
        when(vinculaciones.findByInmuebleIdAndEstado(10L, EstadoVinculacion.VIGENTE))
                .thenReturn(List.of(propietario, arrendatario));

        String html = controller.pazYSalvo(10L, null).getBody();

        assertTrue(html.contains("Ana &lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)"));
        assertFalse(html.contains("Luis"));
    }

    @Test
    void pazYSalvoSinPropietarioLoIndica() {
        when(vinculaciones.findByInmuebleIdAndEstado(10L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());

        assertTrue(controller.pazYSalvo(10L, null).getBody().contains("Sin propietario vinculado"));
    }

    @Test
    void pazYSalvoAceptaUnaFechaDeCorteDeHoy() {
        ResponseEntity<String> r = controller.pazYSalvo(10L, LocalDate.now());

        assertNotNull(r.getBody());
        verify(auditoria, atLeastOnce()).registrar(eq("EXPEDIR_PAZ_Y_SALVO"), any(), any(), contains("Corte " + LocalDate.now()));
    }

    // ---------- Ayudas ----------

    private void assertEstado(HttpStatus esperado, org.junit.jupiter.api.function.Executable accion) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, accion);
        assertEquals(esperado.value(), ex.getStatusCode().value());
    }

    private Inmueble inmueble(long id, String identificacion, String tipoNombre) {
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id);
        i.setIdentificacion(identificacion);
        if (tipoNombre != null) {
            TipoInmueble t = new TipoInmueble();
            t.setNombre(tipoNombre);
            i.setTipoInmueble(t);
        }
        return i;
    }

    /** diasVencida > 0: venció hace esos días; negativo: vence dentro de esos días. */
    private Obligacion obligacion(long id, Inmueble de, ConceptoObligacion concepto, String periodo,
                                  long valor, int diasVencida) {
        Obligacion o = new Obligacion();
        ReflectionTestUtils.setField(o, "id", id);
        o.setInmueble(de);
        o.setConcepto(concepto);
        o.setPeriodo(periodo);
        o.setValor(valor);
        o.setSaldo(valor);
        o.setFechaVencimiento(LocalDate.now().minusDays(diasVencida));
        o.setEstado(EstadoObligacion.PENDIENTE);
        return o;
    }

    private Pago pago(EstadoPago estado, LocalDate fecha, LocalDateTime anuladoEn, Obligacion o, long valor) {
        Pago p = new Pago();
        p.setEstado(estado);
        p.setFecha(fecha);
        p.setAnuladoEn(anuladoEn);
        PagoDetalle d = new PagoDetalle();
        d.setPago(p);
        d.setObligacion(o);
        d.setValor(valor);
        p.getDetalles().add(d);
        return p;
    }

    private Vinculacion vinculacion(TipoVinculo tipo, String nombres, String apellidos) {
        Integrante i = new Integrante();
        i.setNombres(nombres);
        i.setApellidos(apellidos);
        Vinculacion v = new Vinculacion();
        v.setIntegrante(i);
        v.setInmueble(apto101);
        v.setTipoVinculo(tipo);
        return v;
    }
}
