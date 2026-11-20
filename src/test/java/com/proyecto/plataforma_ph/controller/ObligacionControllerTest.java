package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.ObligacionController.ConfigPeriodo;
import com.proyecto.plataforma_ph.controller.ObligacionController.NuevaObligacion;
import com.proyecto.plataforma_ph.controller.ObligacionController.ObligacionDto;
import com.proyecto.plataforma_ph.controller.ObligacionController.PeriodoDto;
import com.proyecto.plataforma_ph.controller.ObligacionController.ResultadoGeneracion;
import com.proyecto.plataforma_ph.controller.ObligacionController.ResumenInmueble;
import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.PeriodoFacturacion;
import com.proyecto.plataforma_ph.model.TipoInmueble;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.PeriodoFacturacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Obligaciones (CU-11): configuración y generación del periodo (RF-065, RF-066, RN-41),
 * obligaciones individuales (RF-067), consulta (RF-068), anulación con motivo (RF-069, RN-27)
 * y consulta del residente (RF-070 a RF-073, RN-22).
 */
class ObligacionControllerTest {

    private ObligacionRepository obligaciones;
    private PeriodoFacturacionRepository periodos;
    private InmuebleRepository inmuebles;
    private VinculacionRepository vinculaciones;
    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private ObligacionController controller;
    private Authentication auth;

    private Inmueble apto101;
    private Integrante yo;

    @BeforeEach
    void preparar() {
        obligaciones = mock(ObligacionRepository.class);
        periodos = mock(PeriodoFacturacionRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        controller = new ObligacionController(obligaciones, periodos, inmuebles, vinculaciones,
                usuarios, auditoria);

        apto101 = inmueble(10L, "Apto 101", "2.5", true, true);
        when(inmuebles.findById(10L)).thenReturn(Optional.of(apto101));

        yo = new Integrante();
        ReflectionTestUtils.setField(yo, "id", 5L);
        Usuario u = new Usuario();
        u.setCorreo("pepe@ph.local");
        u.setIntegrante(yo);
        when(usuarios.buscarConIntegrante("pepe@ph.local")).thenReturn(Optional.of(u));

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("pepe@ph.local");

        // Los repositorios devuelven lo que reciben, como haría la base de datos.
        when(periodos.save(any(PeriodoFacturacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(obligaciones.save(any(Obligacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Periodos (RF-065) ----------

    @Test
    void listarPeriodosDevuelveLosPeriodosConfigurados() {
        PeriodoFacturacion pf = periodo("2026-11", 200_000L, LocalDate.of(2026, 11, 30), null);
        when(periodos.findAllByOrderByPeriodoDesc()).thenReturn(List.of(pf));

        List<PeriodoDto> lista = controller.listarPeriodos();

        assertEquals(1, lista.size());
        assertEquals("2026-11", lista.get(0).periodo());
        assertEquals(200_000L, lista.get(0).valorBase());
        assertNull(lista.get(0).generadoEn());
    }

    @Test
    void configurarGuardaElPeriodoYAudita() {
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.empty());

        PeriodoDto dto = controller.configurar("2026-12",
                new ConfigPeriodo(300_000L, LocalDate.of(2026, 12, 15)));

        assertEquals("2026-12", dto.periodo());
        assertEquals(300_000L, dto.valorBase());
        assertEquals(LocalDate.of(2026, 12, 15), dto.fechaVencimiento());
        verify(periodos).save(any(PeriodoFacturacion.class));
        verify(auditoria).registrar(eq("CONFIGURAR"), eq("PeriodoFacturacion"), any(), anyString());
    }

    @Test
    void configurarPermiteCambiarUnPeriodoAunNoGenerado() {
        PeriodoFacturacion existente = periodo("2026-12", 100_000L, LocalDate.of(2026, 12, 10), null);
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(existente));

        PeriodoDto dto = controller.configurar("2026-12",
                new ConfigPeriodo(150_000L, LocalDate.of(2026, 12, 20)));

        assertEquals(150_000L, dto.valorBase());
        assertEquals(150_000L, existente.getValorBase());
    }

    @Test
    void configurarRechazaFormatoDePeriodoInvalido() {
        for (String malo : new String[] {"2026-13", "2026-00", "26-11", "2026/11", "noviembre", "", null}) {
            assertEstado(HttpStatus.BAD_REQUEST, () -> controller.configurar(malo,
                    new ConfigPeriodo(100_000L, LocalDate.of(2026, 12, 1))));
        }
        verify(periodos, never()).save(any());
    }

    @Test
    void configurarRechazaValorBaseInvalido() {
        LocalDate venc = LocalDate.of(2026, 12, 20);
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.configurar("2026-12", new ConfigPeriodo(null, venc)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.configurar("2026-12", new ConfigPeriodo(0L, venc)));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.configurar("2026-12", new ConfigPeriodo(-5L, venc)));
        assertEstado(HttpStatus.BAD_REQUEST,
                () -> controller.configurar("2026-12", new ConfigPeriodo(1_000_000_000_001L, venc)));
        verify(periodos, never()).save(any());
    }

    @Test
    void configurarExigeFechaDeVencimiento() {
        assertEstado(HttpStatus.BAD_REQUEST,
                () -> controller.configurar("2026-12", new ConfigPeriodo(100_000L, null)));
    }

    @Test
    void configurarRechazaVencimientoAnteriorAlInicioDelPeriodo() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.configurar("2026-12",
                new ConfigPeriodo(100_000L, LocalDate.of(2026, 11, 30))));
        verify(periodos, never()).save(any());
    }

    @Test
    void configurarRechazaUnPeriodoYaGenerado() {
        PeriodoFacturacion generado = periodo("2026-12", 100_000L, LocalDate.of(2026, 12, 10),
                LocalDateTime.now());
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(generado));

        assertEstado(HttpStatus.CONFLICT, () -> controller.configurar("2026-12",
                new ConfigPeriodo(500_000L, LocalDate.of(2026, 12, 20))));
        assertEquals(100_000L, generado.getValorBase());
    }

    // ---------- Generación (RF-066, RN-41) ----------

    @Test
    void generarExigeQueElPeriodoEsteConfigurado() {
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.empty());

        assertEstado(HttpStatus.NOT_FOUND, () -> controller.generar("2026-12", auth));
        verify(obligaciones, never()).save(any());
    }

    @Test
    void generarRechazaFormatoDePeriodoInvalido() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.generar("diciembre", auth));
    }

    @Test
    void generarCalculaLaCuotaPorCoeficienteYSoloParaTiposQueGeneranCuota() {
        PeriodoFacturacion pf = periodo("2026-12", 1_000_000L, LocalDate.of(2026, 12, 15), null);
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(pf));

        Inmueble sinCuota = inmueble(11L, "Bodega 1", "1.0", false, true);   // el tipo no genera cuota
        Inmueble sinCoef = inmueble(12L, "Apto 102", "0", true, true);       // coeficiente cero: se omite
        Inmueble sinTipo = inmueble(13L, "Apto 103", "1.0", true, true);
        sinTipo.setTipoInmueble(null);
        when(inmuebles.findByActivo(true)).thenReturn(List.of(sinCuota, sinCoef, sinTipo, apto101));

        ResultadoGeneracion r = controller.generar("2026-12", auth);

        assertEquals(1, r.creadas());
        assertEquals(0, r.yaExistian());
        assertEquals(1, r.omitidas());

        // RN-41: 1.000.000 x 2,5 % = 25.000, con saldo igual al valor y vencimiento del periodo.
        org.mockito.ArgumentCaptor<Obligacion> captor = org.mockito.ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones).save(captor.capture());
        Obligacion o = captor.getValue();
        assertEquals(25_000L, o.getValor());
        assertEquals(25_000L, o.getSaldo());
        assertEquals(ConceptoObligacion.CUOTA_ADMINISTRACION, o.getConcepto());
        assertEquals("2026-12", o.getPeriodo());
        assertEquals(LocalDate.of(2026, 12, 15), o.getFechaVencimiento());
        assertEquals("pepe@ph.local", o.getCreadaPor());
        assertEquals(EstadoObligacion.PENDIENTE, o.getEstado());
        assertSame(apto101, o.getInmueble());
    }

    @Test
    void generarRedondeaAlPesoMasCercano() {
        PeriodoFacturacion pf = periodo("2026-12", 100_001L, LocalDate.of(2026, 12, 15), null);
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(pf));
        // 100.001 x 0,5 % = 500,005 -> 500
        Inmueble i = inmueble(14L, "Apto 104", "0.5", true, true);
        when(inmuebles.findByActivo(true)).thenReturn(List.of(i));

        controller.generar("2026-12", auth);

        org.mockito.ArgumentCaptor<Obligacion> captor = org.mockito.ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones).save(captor.capture());
        assertEquals(500L, captor.getValue().getValor());
    }

    @Test
    void generarNoDuplicaLaCuotaDeUnPeriodoYaGeneradoParaEseInmueble() {
        PeriodoFacturacion pf = periodo("2026-12", 1_000_000L, LocalDate.of(2026, 12, 15), null);
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(pf));
        when(inmuebles.findByActivo(true)).thenReturn(List.of(apto101));
        when(obligaciones.existsByInmuebleIdAndConceptoAndPeriodoAndEstadoNot(
                10L, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-12", EstadoObligacion.ANULADA))
                .thenReturn(true);

        ResultadoGeneracion r = controller.generar("2026-12", auth);

        assertEquals(0, r.creadas());
        assertEquals(1, r.yaExistian());
        verify(obligaciones, never()).save(any());
    }

    @Test
    void generarMarcaElPeriodoComoGeneradoYAudita() {
        PeriodoFacturacion pf = periodo("2026-12", 1_000_000L, LocalDate.of(2026, 12, 15), null);
        when(periodos.findByPeriodo("2026-12")).thenReturn(Optional.of(pf));
        when(inmuebles.findByActivo(true)).thenReturn(List.of());

        controller.generar("2026-12", auth);

        assertNotNull(pf.getGeneradoEn());
        verify(auditoria).registrar(eq("GENERAR"), eq("PeriodoFacturacion"), any(), anyString());
    }

    // ---------- Obligación individual (RF-067) ----------

    @Test
    void crearRegistraUnaMultaConPeriodoDeSuVencimiento() {
        LocalDate venc = LocalDate.now().plusDays(10);

        ObligacionDto dto = controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 80_000L, venc, "  Ruido  "), auth);

        assertEquals("MULTA", dto.concepto());
        assertEquals(80_000L, dto.valor());
        assertEquals(80_000L, dto.saldo());
        assertEquals("Ruido", dto.descripcion());
        assertEquals(YearMonth.from(venc).toString(), dto.periodo());
        assertEquals("PENDIENTE", dto.estado());
        assertEquals("Apto 101", dto.inmueble());
        verify(auditoria).registrar(eq("CREAR"), eq("Obligacion"), any(), anyString());
    }

    @Test
    void crearAceptaCuotaExtraordinaria() {
        ObligacionDto dto = controller.crear(new NuevaObligacion(10L, ConceptoObligacion.CUOTA_EXTRAORDINARIA,
                1_000_000L, LocalDate.now(), "Reparación de fachada"), auth);

        assertEquals("CUOTA_EXTRAORDINARIA", dto.concepto());
    }

    @Test
    void crearSoloPermiteExtraordinariasOMultas() {
        for (ConceptoObligacion c : new ConceptoObligacion[] {ConceptoObligacion.CUOTA_ADMINISTRACION,
                ConceptoObligacion.INTERES_MORA, ConceptoObligacion.ALQUILER_ZONA, null}) {
            assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                    new NuevaObligacion(10L, c, 50_000L, LocalDate.now().plusDays(5), "x"), auth));
        }
        verify(obligaciones, never()).save(any());
    }

    @Test
    void crearExigeInmueble() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(null, ConceptoObligacion.MULTA, 50_000L, LocalDate.now(), "x"), auth));
    }

    @Test
    void crearRechazaValorInvalido() {
        LocalDate venc = LocalDate.now().plusDays(5);
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, null, venc, "x"), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 0L, venc, "x"), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 1_000_000_000_001L, venc, "x"), auth));
        verify(obligaciones, never()).save(any());
    }

    @Test
    void crearRechazaVencimientoAnteriorAHoyOAusente() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(new NuevaObligacion(
                10L, ConceptoObligacion.MULTA, 50_000L, LocalDate.now().minusDays(1), "x"), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(new NuevaObligacion(
                10L, ConceptoObligacion.MULTA, 50_000L, null, "x"), auth));
    }

    @Test
    void crearExigeDetalleYLimitaSuLongitud() {
        LocalDate venc = LocalDate.now().plusDays(5);
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 50_000L, venc, null), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 50_000L, venc, "   "), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 50_000L, venc, "a".repeat(201)), auth));
        // 200 caracteres es el máximo permitido.
        controller.crear(new NuevaObligacion(10L, ConceptoObligacion.MULTA, 50_000L, venc, "a".repeat(200)), auth);
    }

    @Test
    void crearRechazaInmuebleInexistenteODesactivado() {
        LocalDate venc = LocalDate.now().plusDays(5);
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.crear(
                new NuevaObligacion(999L, ConceptoObligacion.MULTA, 50_000L, venc, "x"), auth));

        apto101.setActivo(false);
        assertEstado(HttpStatus.CONFLICT, () -> controller.crear(
                new NuevaObligacion(10L, ConceptoObligacion.MULTA, 50_000L, venc, "x"), auth));
        verify(obligaciones, never()).save(any());
    }

    // ---------- Consulta del administrador (RF-068) ----------

    @Test
    void listarFiltraPorPeriodoInmuebleEstadoYConcepto() {
        Inmueble otro = inmueble(20L, "Apto 202", "1.0", true, true);
        Obligacion a = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L);
        Obligacion b = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        Obligacion c = obligacion(3L, otro, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-12", 100_000L);
        c.setEstado(EstadoObligacion.PAGADA);
        c.setSaldo(0);
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(c, b, a));

        assertEquals(3, controller.listar(null, null, null, null).size());
        assertEquals(3, controller.listar("", null, null, null).size());
        assertEquals(2, controller.listar("2026-11", null, null, null).size());
        assertEquals(2, controller.listar(null, 10L, null, null).size());
        assertEquals(1, controller.listar(null, null, EstadoObligacion.PAGADA, null).size());
        assertEquals(1, controller.listar(null, null, null, ConceptoObligacion.MULTA).size());
        assertEquals(0, controller.listar("2026-12", 10L, null, null).size());
    }

    @Test
    void listarMarcaLasVencidasConSusDiasDeMora() {
        Obligacion vencida = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L);
        vencida.setFechaVencimiento(LocalDate.now().minusDays(7));
        Obligacion alDia = obligacion(2L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-12", 100_000L);
        alDia.setFechaVencimiento(LocalDate.now().plusDays(7));
        when(obligaciones.findAllByOrderByIdDesc()).thenReturn(List.of(alDia, vencida));

        List<ObligacionDto> lista = controller.listar(null, null, null, null);

        assertTrue(lista.get(1).vencida());
        assertEquals(7, lista.get(1).diasMora());
        assertEquals(false, lista.get(0).vencida());
        assertEquals(0, lista.get(0).diasMora());
    }

    // ---------- Anulación (RF-069, RN-27) ----------

    @Test
    void anularMarcaLaObligacionConSuMotivoYAudita() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        when(obligaciones.findById(1L)).thenReturn(Optional.of(o));

        ObligacionDto dto = controller.anular(1L, "  Multa mal aplicada  ");

        assertEquals("ANULADA", dto.estado());
        assertEquals("Multa mal aplicada", dto.motivoAnulacion());
        assertEquals(EstadoObligacion.ANULADA, o.getEstado());
        assertNotNull(o.getAnuladaEn());
        verify(auditoria).registrar(eq("ANULAR"), eq("Obligacion"), anyLong(), anyString());
    }

    @Test
    void anularExigeMotivo() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        when(obligaciones.findById(1L)).thenReturn(Optional.of(o));

        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.anular(1L, "  "));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.anular(1L, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.anular(1L, "a".repeat(201)));
        assertEquals(EstadoObligacion.PENDIENTE, o.getEstado());
    }

    @Test
    void anularRechazaObligacionInexistente() {
        when(obligaciones.findById(404L)).thenReturn(Optional.empty());

        assertEstado(HttpStatus.NOT_FOUND, () -> controller.anular(404L, "motivo"));
    }

    @Test
    void anularRechazaUnaObligacionYaAnulada() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        o.setEstado(EstadoObligacion.ANULADA);
        when(obligaciones.findById(1L)).thenReturn(Optional.of(o));

        assertEstado(HttpStatus.CONFLICT, () -> controller.anular(1L, "otra vez"));
    }

    @Test
    void anularRechazaUnaObligacionPagada() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        o.setEstado(EstadoObligacion.PAGADA);
        o.setSaldo(0);
        when(obligaciones.findById(1L)).thenReturn(Optional.of(o));

        assertEstado(HttpStatus.CONFLICT, () -> controller.anular(1L, "motivo"));
        assertEquals(EstadoObligacion.PAGADA, o.getEstado());
    }

    @Test
    void anularRechazaUnaObligacionConAbonos() {
        Obligacion o = obligacion(1L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        o.setSaldo(20_000L); // RN-42: ya recibió un abono parcial
        when(obligaciones.findById(1L)).thenReturn(Optional.of(o));

        assertEstado(HttpStatus.CONFLICT, () -> controller.anular(1L, "motivo"));
        assertEquals(EstadoObligacion.PENDIENTE, o.getEstado());
        verify(obligaciones, never()).save(any());
    }

    // ---------- Consulta del residente (RF-070 a RF-073, RN-22) ----------

    @Test
    void miasRechazaUnaCuentaSinIntegrante() {
        when(usuarios.buscarConIntegrante("pepe@ph.local")).thenReturn(Optional.empty());

        assertEstado(HttpStatus.FORBIDDEN, () -> controller.mias(null, auth));
    }

    @Test
    void miasDevuelveListaVaciaSinVinculacionesVigentes() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());

        assertTrue(controller.mias(null, auth).isEmpty());
        verify(obligaciones, never()).findByInmuebleIdInOrderByIdDesc(any());
    }

    @Test
    void miasRechazaUnInmuebleSinVinculacionVigente() {
        vinculado(TipoVinculo.PROPIETARIO_RESIDENTE);

        assertEstado(HttpStatus.FORBIDDEN, () -> controller.mias(99L, auth));
    }

    @Test
    void elPropietarioVeTodasSusObligacionesMenosLasAnuladas() {
        vinculado(TipoVinculo.PROPIETARIO_NO_RESIDENTE);
        Obligacion cuota = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L);
        Obligacion multa = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        Obligacion anulada = obligacion(3L, apto101, ConceptoObligacion.MULTA, "2026-10", 30_000L);
        anulada.setEstado(EstadoObligacion.ANULADA);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any()))
                .thenReturn(List.of(anulada, multa, cuota));

        List<ObligacionDto> lista = controller.mias(null, auth);

        assertEquals(2, lista.size());
        assertTrue(lista.stream().noneMatch(d -> d.estado().equals("ANULADA")));
    }

    @Test
    void elArrendatarioSoloVeLaCuotaOrdinariaYElAlquilerDeZonas() {
        vinculado(TipoVinculo.ARRENDATARIO);
        Obligacion cuota = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-11", 100_000L);
        Obligacion zona = obligacion(2L, apto101, ConceptoObligacion.ALQUILER_ZONA, "2026-11", 40_000L);
        Obligacion multa = obligacion(3L, apto101, ConceptoObligacion.MULTA, "2026-11", 50_000L);
        Obligacion extra = obligacion(4L, apto101, ConceptoObligacion.CUOTA_EXTRAORDINARIA, "2026-11", 70_000L);
        Obligacion mora = obligacion(5L, apto101, ConceptoObligacion.INTERES_MORA, "2026-11", 5_000L);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any()))
                .thenReturn(List.of(mora, extra, multa, zona, cuota));

        List<ObligacionDto> lista = controller.mias(10L, auth);

        assertEquals(2, lista.size());
        assertTrue(lista.stream().map(ObligacionDto::concepto)
                .allMatch(c -> c.equals("CUOTA_ADMINISTRACION") || c.equals("ALQUILER_ZONA")));
    }

    @Test
    void resumenSumaSaldoYSaldoVencidoPorInmueble() {
        vinculado(TipoVinculo.PROPIETARIO_RESIDENTE);
        Obligacion vencida = obligacion(1L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-10", 100_000L);
        vencida.setFechaVencimiento(LocalDate.now().minusDays(3));
        Obligacion aDia = obligacion(2L, apto101, ConceptoObligacion.MULTA, "2026-12", 50_000L);
        aDia.setFechaVencimiento(LocalDate.now().plusDays(3));
        aDia.setSaldo(30_000L);
        Obligacion pagada = obligacion(3L, apto101, ConceptoObligacion.CUOTA_ADMINISTRACION, "2026-09", 100_000L);
        pagada.setEstado(EstadoObligacion.PAGADA);
        pagada.setSaldo(0);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of(aDia, vencida, pagada));

        List<ResumenInmueble> resumen = controller.resumen(auth);

        assertEquals(1, resumen.size());
        ResumenInmueble r = resumen.get(0);
        assertEquals("Apto 101", r.inmueble());
        assertEquals("Propietario", r.vinculo());
        assertEquals(130_000L, r.saldoPendiente());
        assertEquals(100_000L, r.saldoVencido());
        assertEquals(2, r.pendientes());
    }

    @Test
    void resumenIndicaSiElResidenteEsArrendatario() {
        vinculado(TipoVinculo.ARRENDATARIO);
        when(obligaciones.findByInmuebleIdInOrderByIdDesc(any())).thenReturn(List.of());

        List<ResumenInmueble> resumen = controller.resumen(auth);

        assertEquals("Arrendatario", resumen.get(0).vinculo());
        assertEquals(0L, resumen.get(0).saldoPendiente());
    }

    @Test
    void resumenSinVinculacionesDevuelveListaVacia() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());

        assertTrue(controller.resumen(auth).isEmpty());
    }

    // ---------- Ayudas ----------

    private void assertEstado(HttpStatus esperado, org.junit.jupiter.api.function.Executable accion) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, accion);
        assertEquals(esperado.value(), ex.getStatusCode().value());
    }

    private static void assertSame(Object esperado, Object real) {
        org.junit.jupiter.api.Assertions.assertSame(esperado, real);
    }

    private Inmueble inmueble(long id, String identificacion, String coeficiente,
                              boolean generaCuota, boolean activo) {
        TipoInmueble tipo = new TipoInmueble();
        tipo.setGeneraCuota(generaCuota);
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id);
        i.setIdentificacion(identificacion);
        i.setCoeficiente(new BigDecimal(coeficiente));
        i.setTipoInmueble(tipo);
        i.setActivo(activo);
        return i;
    }

    private PeriodoFacturacion periodo(String p, long valorBase, LocalDate vencimiento, LocalDateTime generado) {
        PeriodoFacturacion pf = new PeriodoFacturacion();
        pf.setPeriodo(p);
        pf.setValorBase(valorBase);
        pf.setFechaVencimiento(vencimiento);
        pf.setGeneradoEn(generado);
        return pf;
    }

    private Obligacion obligacion(long id, Inmueble de, ConceptoObligacion concepto, String periodo, long valor) {
        Obligacion o = new Obligacion();
        ReflectionTestUtils.setField(o, "id", id);
        o.setInmueble(de);
        o.setConcepto(concepto);
        o.setPeriodo(periodo);
        o.setValor(valor);
        o.setSaldo(valor);
        o.setFechaVencimiento(LocalDate.now().plusDays(15));
        o.setEstado(EstadoObligacion.PENDIENTE);
        return o;
    }

    private void vinculado(TipoVinculo tipo) {
        Vinculacion v = new Vinculacion();
        v.setIntegrante(yo);
        v.setInmueble(apto101);
        v.setTipoVinculo(tipo);
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of(v));
    }
}
