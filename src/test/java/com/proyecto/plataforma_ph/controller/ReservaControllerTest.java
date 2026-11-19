package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.ReservaController.ConfigZona;
import com.proyecto.plataforma_ph.controller.ReservaController.Disponibilidad;
import com.proyecto.plataforma_ph.controller.ReservaController.NuevaMulta;
import com.proyecto.plataforma_ph.controller.ReservaController.NuevaReserva;
import com.proyecto.plataforma_ph.controller.ReservaController.ReservaDto;
import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.ConfiguracionZona;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EstadoReserva;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.model.Reserva;
import com.proyecto.plataforma_ph.model.TipoInmueble;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reservas de zonas comunes (CU-20 y CU-21): RN-37 (solo tipos que permiten reserva),
 * RN-38 (sin cruces entre reservas confirmadas), RN-39 (la confirmación genera el alquiler),
 * RN-40 (la multa se imputa al inmueble) y RN-43 (solo reserva quien es residente vigente).
 */
class ReservaControllerTest {

    private static final long TARIFA_HORA = 50_000L;

    private ReservaRepository reservas;
    private ConfiguracionZonaRepository configuraciones;
    private InmuebleRepository inmuebles;
    private ObligacionRepository obligaciones;
    private VinculacionRepository vinculaciones;
    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private ReservaController controller;
    private Authentication auth;

    private Inmueble zona;       // salón: por hora, 08:00 a 22:00
    private Inmueble casa;       // inmueble del residente
    private Inmueble otraCasa;   // inmueble ajeno
    private Integrante yo;
    private ConfiguracionZona cfg;
    private LocalDate dia;       // un día futuro para reservar

    @BeforeEach
    void preparar() {
        reservas = mock(ReservaRepository.class);
        configuraciones = mock(ConfiguracionZonaRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        obligaciones = mock(ObligacionRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        controller = new ReservaController(reservas, configuraciones, inmuebles, obligaciones,
                vinculaciones, usuarios, auditoria);

        dia = LocalDate.now().plusDays(3);

        TipoInmueble tipoZona = new TipoInmueble();
        tipoZona.setNombre("Zona comun");
        tipoZona.setPermiteReserva(true);
        zona = inmueble(1L, "Salon social", tipoZona);

        TipoInmueble tipoApto = new TipoInmueble();
        tipoApto.setNombre("Apartamento");
        casa = inmueble(10L, "Apto 101", tipoApto);
        otraCasa = inmueble(20L, "Apto 202", tipoApto);

        when(inmuebles.findById(1L)).thenReturn(Optional.of(zona));
        when(inmuebles.findById(10L)).thenReturn(Optional.of(casa));
        when(inmuebles.findById(20L)).thenReturn(Optional.of(otraCasa));
        when(inmuebles.findByActivo(true)).thenReturn(List.of(zona, casa, otraCasa));

        cfg = configuracion(zona, UnidadCobro.HORA, TARIFA_HORA, 8 * 60, 22 * 60);

        yo = new Integrante();
        ReflectionTestUtils.setField(yo, "id", 5L);
        yo.setNombres("Ana");
        yo.setApellidos("Pérez");
        Usuario u = new Usuario();
        u.setCorreo("ana@ph.local");
        u.setIntegrante(yo);
        when(usuarios.buscarConIntegrante("ana@ph.local")).thenReturn(Optional.of(u));
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ana@ph.local");

        // Ana es propietaria residente de Apto 101.
        vinculos(vinculo(yo, casa, TipoVinculo.PROPIETARIO_RESIDENTE));

        when(reservas.save(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", 50L);
            }
            return r;
        });
        when(obligaciones.save(any(Obligacion.class))).thenAnswer(inv -> {
            Obligacion o = inv.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", 70L);
            }
            return o;
        });
    }

    // ---------- ayudas ----------

    private Inmueble inmueble(long id, String identificacion, TipoInmueble tipo) {
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id);
        i.setIdentificacion(identificacion);
        i.setTipoInmueble(tipo);
        i.setActivo(true);
        return i;
    }

    private ConfiguracionZona configuracion(Inmueble de, UnidadCobro unidad, long tarifa, int apertura, int cierre) {
        ConfiguracionZona c = new ConfiguracionZona();
        c.setZona(de);
        c.setUnidadCobro(unidad);
        c.setTarifa(tarifa);
        c.setAperturaMin(apertura);
        c.setCierreMin(cierre);
        when(configuraciones.findByZonaId(de.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private Vinculacion vinculo(Integrante de, Inmueble en, TipoVinculo tipo) {
        Vinculacion v = new Vinculacion();
        v.setIntegrante(de);
        v.setInmueble(en);
        v.setTipoVinculo(tipo);
        v.setEstado(EstadoVinculacion.VIGENTE);
        return v;
    }

    private void vinculos(Vinculacion... lista) {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of(lista));
    }

    private LocalDateTime a(int hora) {
        return dia.atTime(LocalTime.of(hora, 0));
    }

    private NuevaReserva pedido(int desde, int hasta) {
        return new NuevaReserva(1L, 10L, a(desde), a(hasta), "Cumpleaños");
    }

    private Reserva reserva(long id, EstadoReserva estado, LocalDateTime inicio, LocalDateTime fin) {
        Reserva r = new Reserva();
        ReflectionTestUtils.setField(r, "id", id);
        r.setZona(zona);
        r.setSolicitante(yo);
        r.setInmueble(casa);
        r.setInicio(inicio);
        r.setFin(fin);
        r.setFinalidad("Fiesta");
        r.setEstado(estado);
        r.setValor(100_000L);
        when(reservas.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    private void confirmadas(Reserva... lista) {
        when(reservas.findByZonaIdAndEstado(1L, EstadoReserva.CONFIRMADA)).thenReturn(List.of(lista));
    }

    private Obligacion alquiler(long valor, long saldo) {
        Obligacion o = new Obligacion();
        ReflectionTestUtils.setField(o, "id", 70L);
        o.setInmueble(casa);
        o.setConcepto(ConceptoObligacion.ALQUILER_ZONA);
        o.setValor(valor);
        o.setSaldo(saldo);
        o.setEstado(EstadoObligacion.PENDIENTE);
        return o;
    }

    private Reserva reservaYaOcurrida() {
        LocalDateTime ini = LocalDateTime.now().minusDays(1).withHour(14).withMinute(0);
        return reserva(60L, EstadoReserva.FINALIZADA, ini, ini.plusHours(3));
    }

    private HttpStatus estado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // =====================================================================
    // Solicitar (RF-116) y disponibilidad (RF-115)
    // =====================================================================

    @Test
    void solicitarCalculaElValorPorHoraYQuedaSolicitada() {
        ReservaDto r = controller.solicitar(pedido(14, 17), auth);

        assertEquals("SOLICITADA", r.estado());
        assertEquals(3 * TARIFA_HORA, r.valor());
        verify(auditoria).registrar(any(), any(), any(), any());
    }

    @Test
    void lasHorasSeCobranRedondeadasHaciaArriba() {
        Disponibilidad d = controller.disponibilidad(1L, a(14), a(14).plusMinutes(90));

        assertTrue(d.disponible());
        assertEquals(2, d.unidades());
        assertEquals(2 * TARIFA_HORA, d.valor());
    }

    @Test
    void laTarifaPorEventoSeCobraUnaSolaVez() {
        cfg.setUnidadCobro(UnidadCobro.EVENTO);
        cfg.setTarifa(80_000L);

        ReservaDto r = controller.solicitar(pedido(10, 20), auth);

        assertEquals(80_000L, r.valor());
    }

    @Test
    void noSePuedeReservarFueraDelHorarioDeLaZona() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(pedido(7, 9), auth)));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(pedido(21, 23), auth)));
    }

    @Test
    void noSePuedeReservarEnElPasado() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        NuevaReserva pasada = new NuevaReserva(1L, 10L, ayer.atTime(10, 0), ayer.atTime(12, 0), "Pasada");

        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(pasada, auth)));
    }

    @Test
    void laHoraFinalDebeSerPosteriorYElMismoDia() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(pedido(12, 11), auth)));

        NuevaReserva otroDia = new NuevaReserva(1L, 10L, a(20), a(20).plusDays(1).plusHours(1), "Dos dias");
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(otroDia, auth)));
    }

    @Test
    void rn38_noSePuedeCruzarConUnaReservaConfirmada() {
        confirmadas(reserva(60L, EstadoReserva.CONFIRMADA, a(14), a(17)));

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.solicitar(pedido(16, 18), auth)));
        verify(reservas, never()).save(any(Reserva.class));
    }

    @Test
    void unaReservaQueEmpiezaCuandoOtraTerminaNoSeCruza() {
        confirmadas(reserva(60L, EstadoReserva.CONFIRMADA, a(14), a(17)));

        ReservaDto r = controller.solicitar(pedido(17, 19), auth);

        assertEquals("SOLICITADA", r.estado());
    }

    @Test
    void rn43_quienNoEsResidenteNoPuedeReservar() {
        // Solo es propietaria no residente: no cuenta como residente.
        vinculos(vinculo(yo, casa, TipoVinculo.PROPIETARIO_NO_RESIDENTE));

        assertEquals(HttpStatus.FORBIDDEN, estado(() -> controller.solicitar(pedido(14, 17), auth)));
    }

    @Test
    void elArrendatarioYElResidenteAutorizadoSiPuedenReservar() {
        vinculos(vinculo(yo, casa, TipoVinculo.ARRENDATARIO));
        assertEquals("SOLICITADA", controller.solicitar(pedido(14, 15), auth).estado());

        vinculos(vinculo(yo, casa, TipoVinculo.RESIDENTE_AUTORIZADO));
        assertEquals("SOLICITADA", controller.solicitar(pedido(16, 17), auth).estado());
    }

    @Test
    void elAlquilerSoloSeImputaAUnInmuebleDondeSoyResidente() {
        NuevaReserva ajeno = new NuevaReserva(1L, 20L, a(14), a(17), "Fiesta");

        assertEquals(HttpStatus.FORBIDDEN, estado(() -> controller.solicitar(ajeno, auth)));
    }

    @Test
    void laFinalidadEsObligatoria() {
        NuevaReserva sinFinalidad = new NuevaReserva(1L, 10L, a(14), a(17), "  ");

        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.solicitar(sinFinalidad, auth)));
    }

    @Test
    void rn37_soloSeReservanInmueblesCuyoTipoPermiteReserva() {
        NuevaReserva aApto = new NuevaReserva(10L, 10L, a(14), a(17), "Fiesta");

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.solicitar(aApto, auth)));
    }

    @Test
    void unaZonaSinTarifaNiHorarioNoSePuedeReservar() {
        when(configuraciones.findByZonaId(1L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.solicitar(pedido(14, 17), auth)));
    }

    // =====================================================================
    // Confirmar y rechazar (RF-119)
    // =====================================================================

    @Test
    void rn39_confirmarGeneraElAlquilerDeLaZona() {
        Reserva r = reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));

        ReservaDto dto = controller.confirmar(60L, auth);

        assertEquals("CONFIRMADA", dto.estado());
        Obligacion o = r.getObligacion();
        assertNotNull(o);
        assertEquals(ConceptoObligacion.ALQUILER_ZONA, o.getConcepto());
        assertEquals(100_000L, o.getValor());
        assertEquals(100_000L, o.getSaldo());
        assertEquals(EstadoObligacion.PENDIENTE, o.getEstado());
        assertEquals(casa, o.getInmueble());
        assertEquals(dia, o.getFechaVencimiento());
    }

    @Test
    void unaReservaGratisNoGeneraObligacion() {
        Reserva r = reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));
        r.setValor(0L);

        controller.confirmar(60L, auth);

        assertNull(r.getObligacion());
        verify(obligaciones, never()).save(any(Obligacion.class));
    }

    @Test
    void soloSeConfirmanReservasSolicitadas() {
        reserva(60L, EstadoReserva.CONFIRMADA, a(14), a(17));

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.confirmar(60L, auth)));
    }

    @Test
    void rn38_noSeConfirmaSiOtraConfirmadaYaOcupaElHorario() {
        reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));
        confirmadas(reserva(61L, EstadoReserva.CONFIRMADA, a(15), a(18)));

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.confirmar(60L, auth)));
    }

    @Test
    void rechazarExigeMotivoYSoloAplicaASolicitadas() {
        Reserva r = reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.rechazar(60L, "  ")));

        assertEquals("RECHAZADA", controller.rechazar(60L, "Mantenimiento del salón").estado());
        assertEquals("Mantenimiento del salón", r.getMotivoCierre());

        reserva(61L, EstadoReserva.CONFIRMADA, a(14), a(17));
        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.rechazar(61L, "Otro")));
    }

    // =====================================================================
    // Cancelar (RF-118)
    // =====================================================================

    @Test
    void cancelarUnaReservaConfirmadaAnulaElAlquiler() {
        Reserva r = reserva(60L, EstadoReserva.CONFIRMADA, a(14), a(17));
        Obligacion o = alquiler(100_000L, 100_000L);
        r.setObligacion(o);

        ReservaDto dto = controller.cancelar(60L, "Cambió el plan", auth);

        assertEquals("CANCELADA", dto.estado());
        assertEquals(EstadoObligacion.ANULADA, o.getEstado());
        verify(auditoria).registrar(any(), any(), any(), any());
    }

    @Test
    void noSePuedeCancelarUnaReservaDeOtraPersona() {
        Integrante otro = new Integrante();
        ReflectionTestUtils.setField(otro, "id", 77L);
        Reserva r = reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));
        r.setSolicitante(otro);

        assertEquals(HttpStatus.FORBIDDEN, estado(() -> controller.cancelar(60L, "Motivo", auth)));
    }

    @Test
    void noSeCancelaSiElAlquilerYaTienePagos() {
        Reserva r = reserva(60L, EstadoReserva.CONFIRMADA, a(14), a(17));
        Obligacion o = alquiler(100_000L, 40_000L); // ya se abonó
        r.setObligacion(o);

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.cancelar(60L, "Motivo", auth)));
        assertEquals(EstadoObligacion.PENDIENTE, o.getEstado());
        assertEquals(EstadoReserva.CONFIRMADA, r.getEstado());
    }

    @Test
    void noSeCancelaUnaReservaQueYaComenzo() {
        LocalDateTime ini = LocalDateTime.now().minusHours(2);
        reserva(60L, EstadoReserva.CONFIRMADA, ini, ini.plusHours(4));

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.cancelar(60L, "Motivo", auth)));
    }

    @Test
    void cancelarExigeMotivo() {
        reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));

        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.cancelar(60L, " ", auth)));
    }

    // =====================================================================
    // Finalizar (RF-121)
    // =====================================================================

    @Test
    void finalizarUnaReservaConfirmadaGuardaLaFecha() {
        LocalDateTime ini = LocalDateTime.now().minusDays(1).withHour(14).withMinute(0);
        Reserva r = reserva(60L, EstadoReserva.CONFIRMADA, ini, ini.plusHours(3));

        ReservaDto dto = controller.finalizar(60L, LocalDate.now());

        assertEquals("FINALIZADA", dto.estado());
        assertEquals(LocalDate.now(), r.getFechaFinalizacion());
    }

    @Test
    void finalizarSoloAplicaAReservasConfirmadas() {
        reserva(60L, EstadoReserva.SOLICITADA, a(14), a(17));

        assertEquals(HttpStatus.CONFLICT, estado(() -> controller.finalizar(60L, null)));
    }

    @Test
    void laFechaDeFinalizacionNoPuedeSerFuturaNiAnteriorALaReserva() {
        LocalDateTime ini = LocalDateTime.now().minusDays(2).withHour(14).withMinute(0);
        reserva(60L, EstadoReserva.CONFIRMADA, ini, ini.plusHours(3));

        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.finalizar(60L, LocalDate.now().plusDays(1))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.finalizar(60L, ini.toLocalDate().minusDays(1))));
    }

    // =====================================================================
    // Multa (RF-122, RN-40)
    // =====================================================================

    @Test
    void rn40_laMultaSeImputaAlInmuebleIndicadoYVenceEnQuinceDias() {
        Reserva r = reservaYaOcurrida();

        ReservaDto dto = controller.multar(60L, new NuevaMulta(20L, 30_000L, "Daños en el salón"), auth);

        Obligacion multa = r.getMulta();
        assertNotNull(multa);
        assertEquals(ConceptoObligacion.MULTA, multa.getConcepto());
        assertEquals(30_000L, multa.getValor());
        assertEquals(otraCasa, multa.getInmueble());
        assertEquals(LocalDate.now().plusDays(15), multa.getFechaVencimiento());
        assertEquals(30_000L, dto.multaValor());
    }

    @Test
    void sinInmuebleIndicadoLaMultaVaAlInmuebleDeLaReserva() {
        Reserva r = reservaYaOcurrida();

        controller.multar(60L, new NuevaMulta(null, 30_000L, "Ruido"), auth);

        assertEquals(casa, r.getMulta().getInmueble());
    }

    @Test
    void unaReservaSoloTieneUnaMulta() {
        Reserva r = reservaYaOcurrida();
        controller.multar(60L, new NuevaMulta(null, 30_000L, "Ruido"), auth);

        assertEquals(HttpStatus.CONFLICT,
                estado(() -> controller.multar(60L, new NuevaMulta(null, 10_000L, "Otra"), auth)));
        assertEquals(30_000L, r.getMulta().getValor());
    }

    @Test
    void laMultaExigeValorYMotivo() {
        reservaYaOcurrida();

        assertEquals(HttpStatus.BAD_REQUEST,
                estado(() -> controller.multar(60L, new NuevaMulta(null, 0L, "Ruido"), auth)));
        assertEquals(HttpStatus.BAD_REQUEST,
                estado(() -> controller.multar(60L, new NuevaMulta(null, 5_000L, " "), auth)));
    }

    @Test
    void noSeMultaUnaReservaSolicitadaNiUnaQueAunNoLlega() {
        reserva(61L, EstadoReserva.SOLICITADA, a(14), a(17));
        assertEquals(HttpStatus.CONFLICT,
                estado(() -> controller.multar(61L, new NuevaMulta(null, 5_000L, "Ruido"), auth)));

        reserva(62L, EstadoReserva.CONFIRMADA, a(14), a(17)); // dentro de 3 días
        assertEquals(HttpStatus.CONFLICT,
                estado(() -> controller.multar(62L, new NuevaMulta(null, 5_000L, "Ruido"), auth)));
    }

    // =====================================================================
    // Configurar la zona (administrador)
    // =====================================================================

    @Test
    void configurarUnaZonaValidaTarifaYHorario() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() ->
                controller.configurarZona(1L, new ConfigZona(UnidadCobro.HORA, 50_000L, "22:00", "08:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() ->
                controller.configurarZona(1L, new ConfigZona(UnidadCobro.HORA, -1L, "08:00", "22:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() ->
                controller.configurarZona(1L, new ConfigZona(UnidadCobro.HORA, 50_000L, "ocho", "22:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() ->
                controller.configurarZona(1L, new ConfigZona(null, 50_000L, "08:00", "22:00"))));
    }

    @Test
    void configurarUnaZonaGuardaLosNuevosValores() {
        var z = controller.configurarZona(1L, new ConfigZona(UnidadCobro.DIA, 120_000L, "09:00", "20:30"));

        assertEquals("DIA", z.unidadCobro());
        assertEquals(120_000L, z.tarifa());
        assertEquals("09:00", z.horaApertura());
        assertEquals("20:30", z.horaCierre());
    }

    @Test
    void noSeConfiguraUnInmuebleQueNoAdmiteReservas() {
        assertEquals(HttpStatus.CONFLICT, estado(() ->
                controller.configurarZona(10L, new ConfigZona(UnidadCobro.HORA, 50_000L, "08:00", "22:00"))));
    }
}