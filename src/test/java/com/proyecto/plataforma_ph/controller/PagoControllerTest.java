package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.PagoController.ItemPago;
import com.proyecto.plataforma_ph.controller.PagoController.NuevoPagoPasarela;
import com.proyecto.plataforma_ph.controller.PagoController.NuevoPagoPresencial;
import com.proyecto.plataforma_ph.controller.PagoController.PagoDto;
import com.proyecto.plataforma_ph.model.CanalPago;
import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoPago;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.MedioPago;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.pasarela.PasarelaPago;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import com.proyecto.plataforma_ph.repository.PagoRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pagos (CU-12 y CU-13) y sus reglas: RN-22 (el arrendatario solo paga la cuota ordinaria),
 * RN-23 (la pasarela confirma), RN-24 (se registra quién cobró), RN-27 (anular con motivo)
 * y RN-42 (abonos parciales).
 */
class PagoControllerTest {

    private static final long CUOTA = 100_000L;
    private static final long MULTA = 50_000L;

    private PagoRepository pagos;
    private ObligacionRepository obligaciones;
    private InmuebleRepository inmuebles;
    private VinculacionRepository vinculaciones;
    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private PasarelaPago pasarela;
    private PagoController controller;
    private Authentication auth;

    private Inmueble inmueble;
    private Inmueble otroInmueble;
    private Integrante yo;
    private Obligacion cuota;
    private Obligacion multa;

    @BeforeEach
    void preparar() {
        pagos = mock(PagoRepository.class);
        obligaciones = mock(ObligacionRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        pasarela = mock(PasarelaPago.class);
        controller = new PagoController(pagos, obligaciones, inmuebles, vinculaciones, usuarios, auditoria, pasarela);

        inmueble = inmueble(10L, "Apto 101");
        otroInmueble = inmueble(20L, "Apto 202");
        when(inmuebles.findById(10L)).thenReturn(Optional.of(inmueble));
        when(inmuebles.findById(20L)).thenReturn(Optional.of(otroInmueble));

        cuota = obligacion(1L, inmueble, ConceptoObligacion.CUOTA_ADMINISTRACION, CUOTA);
        multa = obligacion(2L, inmueble, ConceptoObligacion.MULTA, MULTA);
        when(obligaciones.findById(1L)).thenReturn(Optional.of(cuota));
        when(obligaciones.findById(2L)).thenReturn(Optional.of(multa));

        yo = new Integrante();
        ReflectionTestUtils.setField(yo, "id", 5L);
        yo.setNombres("Pepe");
        yo.setApellidos("Gómez");
        Usuario u = new Usuario();
        u.setCorreo("pepe@ph.local");
        u.setIntegrante(yo);
        when(usuarios.buscarConIntegrante("pepe@ph.local")).thenReturn(Optional.of(u));

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("pepe@ph.local");

        // save() devuelve el mismo pago y le asigna un número, como haría la base de datos.
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                ReflectionTestUtils.setField(p, "id", 99L);
            }
            return p;
        });
        when(pasarela.nombre()).thenReturn("Simulada");
        when(pasarela.iniciar(any(Pago.class), any())).thenReturn(new PasarelaPago.SesionPago("/pasarela-simulada/X"));
    }

    // ---------- ayudas ----------

    private Inmueble inmueble(long id, String identificacion) {
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id);
        i.setIdentificacion(identificacion);
        return i;
    }

    private Obligacion obligacion(long id, Inmueble de, ConceptoObligacion concepto, long valor) {
        Obligacion o = new Obligacion();
        ReflectionTestUtils.setField(o, "id", id);
        o.setInmueble(de);
        o.setConcepto(concepto);
        o.setPeriodo("2026-11");
        o.setValor(valor);
        o.setSaldo(valor);
        o.setEstado(EstadoObligacion.PENDIENTE);
        return o;
    }

    private void vinculadoComo(TipoVinculo tipo) {
        Vinculacion v = new Vinculacion();
        v.setIntegrante(yo);
        v.setInmueble(inmueble);
        v.setTipoVinculo(tipo);
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of(v));
    }

    private Pago pagoExistente(long id, CanalPago canal, EstadoPago estado, Obligacion o, long valor) {
        Pago p = new Pago();
        ReflectionTestUtils.setField(p, "id", id);
        p.setInmueble(inmueble);
        p.setCanal(canal);
        p.setMedio(canal == CanalPago.PASARELA ? MedioPago.PASARELA : MedioPago.EFECTIVO);
        p.setEstado(estado);
        p.setFecha(LocalDate.now());
        p.setPagador("Pepe Gómez");
        p.setReferencia("PH-TEST" + id);
        PagoDetalle d = new PagoDetalle();
        d.setPago(p);
        d.setObligacion(o);
        d.setValor(valor);
        p.getDetalles().add(d);
        when(pagos.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private NuevoPagoPresencial presencial(Long inmuebleId, MedioPago medio, LocalDate fecha,
                                           String pagador, ItemPago... items) {
        return new NuevoPagoPresencial(inmuebleId, medio, fecha, pagador, List.of(items));
    }

    private static HttpStatus estado(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private HttpStatus estadoDe(Runnable accion) {
        return estado(assertThrows(ResponseStatusException.class, accion::run));
    }

    // ---------- pago presencial (RF-079, RN-24, RN-42) ----------

    @Test
    void pagoPresencialCompletoDejaLaObligacionPagadaYRegistraQuienCobro() {
        PagoDto dto = controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "  Pedro  ", new ItemPago(1L, CUOTA)), auth);

        assertEquals("CONFIRMADO", dto.estado());
        assertEquals("PRESENCIAL", dto.canal());
        assertEquals(CUOTA, dto.valor());
        assertEquals("Pedro", dto.pagador());
        assertEquals("pepe@ph.local", dto.registradoPor());
        assertEquals(0L, cuota.getSaldo());
        assertEquals(EstadoObligacion.PAGADA, cuota.getEstado());
        verify(obligaciones).save(cuota);
        verify(auditoria).registrar(any(), any(), any(), any());
    }

    @Test
    void abonoParcialBajaElSaldoYLaObligacionSigueDePendiente() {
        controller.registrarPresencial(
                presencial(10L, MedioPago.TRANSFERENCIA, null, "Pedro", new ItemPago(1L, 30_000L)), auth);

        assertEquals(CUOTA - 30_000L, cuota.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, cuota.getEstado());
    }

    @Test
    void dosAbonosQueSumanElTotalDejanLaObligacionPagada() {
        controller.registrarPresencial(
                presencial(10L, MedioPago.CHEQUE, null, "Pedro", new ItemPago(1L, 60_000L)), auth);
        controller.registrarPresencial(
                presencial(10L, MedioPago.CHEQUE, null, "Pedro", new ItemPago(1L, 40_000L)), auth);

        assertEquals(0L, cuota.getSaldo());
        assertEquals(EstadoObligacion.PAGADA, cuota.getEstado());
    }

    @Test
    void pagoPresencialPuedeIncluirVariasObligaciones() {
        PagoDto dto = controller.registrarPresencial(presencial(10L, MedioPago.EFECTIVO, null, "Pedro",
                new ItemPago(1L, CUOTA), new ItemPago(2L, MULTA)), auth);

        assertEquals(CUOTA + MULTA, dto.valor());
        assertEquals(EstadoObligacion.PAGADA, cuota.getEstado());
        assertEquals(EstadoObligacion.PAGADA, multa.getEstado());
    }

    @Test
    void rechazaUnValorMayorQueElSaldo() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, CUOTA + 1)), auth)));
        assertEquals(CUOTA, cuota.getSaldo());
        verify(pagos, never()).save(any());
    }

    @Test
    void rechazaValoresCeroONegativos() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, 0L)), auth)));
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, -5L)), auth)));
    }

    @Test
    void rechazaPagarDosVecesLaMismaObligacionEnUnPago() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(presencial(
                10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, 10_000L), new ItemPago(1L, 10_000L)), auth)));
    }

    @Test
    void rechazaMedioQueNoSeaPresencial() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.PASARELA, null, "Pedro", new ItemPago(1L, CUOTA)), auth)));
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, null, null, "Pedro", new ItemPago(1L, CUOTA)), auth)));
    }

    @Test
    void rechazaPagadorVacio() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "   ", new ItemPago(1L, CUOTA)), auth)));
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, null, new ItemPago(1L, CUOTA)), auth)));
    }

    @Test
    void rechazaFechaFutura() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, LocalDate.now().plusDays(1), "Pedro",
                        new ItemPago(1L, CUOTA)), auth)));
    }

    @Test
    void rechazaPagoSinObligaciones() {
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                new NuevoPagoPresencial(10L, MedioPago.EFECTIVO, null, "Pedro", List.of()), auth)));
    }

    @Test
    void rechazaInmuebleInexistenteOSinIndicar() {
        when(inmuebles.findById(77L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, estadoDe(() -> controller.registrarPresencial(
                presencial(77L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, CUOTA)), auth)));
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(null, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, CUOTA)), auth)));
    }

    @Test
    void rechazaUnaObligacionQueEsDeOtroInmueble() {
        // El pago es del apto 202, pero la obligación 1 es del apto 101.
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.registrarPresencial(
                presencial(20L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, CUOTA)), auth)));
        assertEquals(CUOTA, cuota.getSaldo());
    }

    @Test
    void rechazaUnaObligacionQueYaNoEstaPendiente() {
        cuota.setEstado(EstadoObligacion.PAGADA);
        cuota.setSaldo(0L);
        assertEquals(HttpStatus.CONFLICT, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(1L, 1_000L)), auth)));

        multa.setEstado(EstadoObligacion.ANULADA);
        assertEquals(HttpStatus.CONFLICT, estadoDe(() -> controller.registrarPresencial(
                presencial(10L, MedioPago.EFECTIVO, null, "Pedro", new ItemPago(2L, 1_000L)), auth)));
    }

    // ---------- pago por pasarela (RF-074, RF-075, RN-22) ----------

    @Test
    void propietarioPuedePagarCualquierConcepto() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);

        var inicio = controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(1L, CUOTA), new ItemPago(2L, MULTA))), auth);

        assertNotNull(inicio.referencia());
        assertTrue(inicio.referencia().startsWith("PH-"));
        verify(pagos).save(any(Pago.class));
    }

    @Test
    void arrendatarioNoPuedePagarMultasNiCuotasExtraordinarias() {
        vinculadoComo(TipoVinculo.ARRENDATARIO);
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(2L, MULTA))), auth)));

        Obligacion extra = obligacion(3L, inmueble, ConceptoObligacion.CUOTA_EXTRAORDINARIA, 80_000L);
        when(obligaciones.findById(3L)).thenReturn(Optional.of(extra));
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(3L, 80_000L))), auth)));

        Obligacion mora = obligacion(4L, inmueble, ConceptoObligacion.INTERES_MORA, 5_000L);
        when(obligaciones.findById(4L)).thenReturn(Optional.of(mora));
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(4L, 5_000L))), auth)));

        verify(pagos, never()).save(any());
    }

    @Test
    void arrendatarioSiPuedePagarLaCuotaDeAdministracion() {
        vinculadoComo(TipoVinculo.ARRENDATARIO);

        var inicio = controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(1L, 40_000L))), auth);

        assertNotNull(inicio.referencia());
        verify(pagos).save(any(Pago.class));
    }

    @Test
    void sinVinculacionVigenteNoSePuedePagarElInmueble() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(1L, CUOTA))), auth)));
        verify(pagos, never()).save(any());
    }

    @Test
    void residenteAutorizadoNoTieneAlcanceDePagos() {
        vinculadoComo(TipoVinculo.RESIDENTE_AUTORIZADO);
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.iniciarPasarela(
                new NuevoPagoPasarela(10L, List.of(new ItemPago(1L, CUOTA))), auth)));
    }

    @Test
    void iniciarElPagoPorPasarelaNoTocaElSaldoHastaQueSeConfirme() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        controller.iniciarPasarela(new NuevoPagoPasarela(10L, List.of(new ItemPago(1L, CUOTA))), auth);

        assertEquals(CUOTA, cuota.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, cuota.getEstado());
        verify(obligaciones, never()).save(any());
    }

    // ---------- verificar con la pasarela (RN-23) ----------

    @Test
    void soloConfirmaElPagoCuandoLaPasarelaLoApruebaYAplicaElSaldo() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        Pago p = pagoExistente(50L, CanalPago.PASARELA, EstadoPago.PENDIENTE, cuota, CUOTA);
        when(pasarela.consultar(p)).thenReturn(
                new PasarelaPago.ResultadoPasarela(PasarelaPago.Estado.APROBADA, "TX-1"));

        PagoDto dto = controller.verificar(50L, auth);

        assertEquals("CONFIRMADO", dto.estado());
        assertEquals("TX-1", dto.transaccionId());
        assertNotNull(p.getConfirmadoEn());
        assertEquals(0L, cuota.getSaldo());
        assertEquals(EstadoObligacion.PAGADA, cuota.getEstado());
    }

    @Test
    void siLaPasarelaSigueEnProcesoElPagoNoSeConfirmaNiCambiaElSaldo() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        Pago p = pagoExistente(51L, CanalPago.PASARELA, EstadoPago.PENDIENTE, cuota, CUOTA);
        when(pasarela.consultar(p)).thenReturn(
                new PasarelaPago.ResultadoPasarela(PasarelaPago.Estado.PENDIENTE, null));

        PagoDto dto = controller.verificar(51L, auth);

        assertEquals("PENDIENTE", dto.estado());
        assertEquals(CUOTA, cuota.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, cuota.getEstado());
    }

    @Test
    void siLaPasarelaRechazaElPagoQuedaRechazadoYElSaldoIgual() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        Pago p = pagoExistente(52L, CanalPago.PASARELA, EstadoPago.PENDIENTE, cuota, CUOTA);
        when(pasarela.consultar(p)).thenReturn(
                new PasarelaPago.ResultadoPasarela(PasarelaPago.Estado.RECHAZADA, "TX-2"));

        PagoDto dto = controller.verificar(52L, auth);

        assertEquals("RECHAZADO", dto.estado());
        assertEquals(CUOTA, cuota.getSaldo());
    }

    @Test
    void verificarUnPagoYaConfirmadoNoVuelveAAplicarElSaldo() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        cuota.setSaldo(0L);
        cuota.setEstado(EstadoObligacion.PAGADA);
        pagoExistente(53L, CanalPago.PASARELA, EstadoPago.CONFIRMADO, cuota, CUOTA);

        PagoDto dto = controller.verificar(53L, auth);

        assertEquals("CONFIRMADO", dto.estado());
        assertEquals(0L, cuota.getSaldo());
        verify(pasarela, never()).consultar(any());
    }

    @Test
    void nadieMasPuedeVerificarUnPagoAjeno() {
        // Pepe no tiene vinculación vigente con el inmueble del pago.
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());
        pagoExistente(54L, CanalPago.PASARELA, EstadoPago.PENDIENTE, cuota, CUOTA);

        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.verificar(54L, auth)));
        verify(pasarela, never()).consultar(any());
    }

    @Test
    void verificarUnPagoPresencialNoEstaPermitido() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        pagoExistente(55L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, CUOTA);
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.verificar(55L, auth)));
    }

    // ---------- anular un pago presencial (RF-081, RN-27) ----------

    @Test
    void anularExigeMotivo() {
        pagoExistente(60L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, CUOTA);
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.anular(60L, "   ")));
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.anular(60L, null)));
    }

    @Test
    void anularDevuelveElSaldoYReabreLaObligacion() {
        cuota.setSaldo(0L);
        cuota.setEstado(EstadoObligacion.PAGADA);
        Pago p = pagoExistente(61L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, CUOTA);

        PagoDto dto = controller.anular(61L, "  Se registró dos veces  ");

        assertEquals("ANULADO", dto.estado());
        assertEquals("Se registró dos veces", dto.motivoAnulacion());
        assertEquals(EstadoPago.ANULADO, p.getEstado());
        assertNotNull(p.getAnuladoEn());
        assertEquals(CUOTA, cuota.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, cuota.getEstado());
        verify(auditoria).registrar("ANULAR_PAGO", "Pago", 61L, "Motivo: Se registró dos veces");
    }

    @Test
    void anularUnAbonoParcialSoloDevuelveLoQueSePago() {
        cuota.setSaldo(70_000L);
        pagoExistente(62L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, 30_000L);

        controller.anular(62L, "Error de digitación");

        assertEquals(CUOTA, cuota.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, cuota.getEstado());
    }

    @Test
    void noSeAnulaDosVecesElMismoPago() {
        pagoExistente(63L, CanalPago.PRESENCIAL, EstadoPago.ANULADO, cuota, CUOTA);
        assertEquals(HttpStatus.CONFLICT, estadoDe(() -> controller.anular(63L, "Otra vez")));
        assertEquals(CUOTA, cuota.getSaldo());
    }

    @Test
    void soloSeAnulanPagosPresenciales() {
        pagoExistente(64L, CanalPago.PASARELA, EstadoPago.CONFIRMADO, cuota, CUOTA);
        assertEquals(HttpStatus.BAD_REQUEST, estadoDe(() -> controller.anular(64L, "Motivo")));
    }

    @Test
    void anularUnPagoInexistenteDevuelveNoEncontrado() {
        when(pagos.findById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, estadoDe(() -> controller.anular(404L, "Motivo")));
    }

    // ---------- historial del residente (RF-078, RN-22) ----------

    @Test
    void elArrendatarioNoVePagosQueIncluyenMultas() {
        vinculadoComo(TipoVinculo.ARRENDATARIO);
        Pago deCuota = pagoExistente(70L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, 10_000L);
        Pago deMulta = pagoExistente(71L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, multa, 10_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(anyCollection())).thenReturn(List.of(deMulta, deCuota));

        List<PagoDto> lista = controller.mios(null, null, null, auth);

        assertEquals(1, lista.size());
        assertEquals(70L, lista.get(0).id());
    }

    @Test
    void elPropietarioVeTodosLosPagosDeSuInmueble() {
        vinculadoComo(TipoVinculo.PROPIETARIO_NO_RESIDENTE);
        Pago deCuota = pagoExistente(72L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, cuota, 10_000L);
        Pago deMulta = pagoExistente(73L, CanalPago.PRESENCIAL, EstadoPago.CONFIRMADO, multa, 10_000L);
        when(pagos.findByInmuebleIdInOrderByIdDesc(anyCollection())).thenReturn(List.of(deMulta, deCuota));

        assertEquals(2, controller.mios(null, null, null, auth).size());
    }

    @Test
    void noSePuedeConsultarElHistorialDeUnInmuebleAjeno() {
        vinculadoComo(TipoVinculo.PROPIETARIO_RESIDENTE);
        assertEquals(HttpStatus.FORBIDDEN, estadoDe(() -> controller.mios(20L, null, null, auth)));
    }

    @Test
    void sinVinculacionesElHistorialEstaVacio() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());
        assertTrue(controller.mios(null, null, null, auth).isEmpty());
    }
}
