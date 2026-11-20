package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.PorteriaController.AccesoDto;
import com.proyecto.plataforma_ph.controller.PorteriaController.AutorizacionPorteriaDto;
import com.proyecto.plataforma_ph.controller.PorteriaController.IngresoRequest;
import com.proyecto.plataforma_ph.controller.PorteriaController.SinAutorizacionRequest;
import com.proyecto.plataforma_ph.model.Autorizacion;
import com.proyecto.plataforma_ph.model.EstadoAutorizacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.MovimientoAcceso;
import com.proyecto.plataforma_ph.model.TipoAutorizacion;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.AutorizacionRepository;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.MovimientoAccesoRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Portería (CU-07): búsqueda de autorizaciones (RF-031, RF-032, RF-043), ingreso con autorización
 * (RF-033, RF-044, RN-09), ingreso sin autorización con motivo (RF-035, RN-12), salida (RF-034),
 * quién está dentro (RF-036) e historial (RF-037).
 * Las reglas de día y franja horaria dependen de la hora actual, así que cada prueba arma su
 * autorización a partir de "hoy" y "ahora".
 */
class PorteriaControllerTest {

    private AutorizacionRepository autorizaciones;
    private MovimientoAccesoRepository movimientos;
    private InmuebleRepository inmuebles;
    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private NotificacionService notificaciones;
    private PorteriaController controller;
    private Authentication auth;

    private Inmueble apto101;
    private Integrante vigilante;

    @BeforeEach
    void preparar() {
        autorizaciones = mock(AutorizacionRepository.class);
        movimientos = mock(MovimientoAccesoRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        notificaciones = mock(NotificacionService.class);
        controller = new PorteriaController(autorizaciones, movimientos, inmuebles, usuarios,
                auditoria, notificaciones);

        apto101 = inmueble(10L, "Apto 101");
        when(inmuebles.findById(10L)).thenReturn(Optional.of(apto101));

        vigilante = new Integrante();
        ReflectionTestUtils.setField(vigilante, "id", 7L);
        vigilante.setNombres("Victor");
        vigilante.setApellidos("Vigia");
        Usuario u = new Usuario();
        u.setCorreo("vigilante@ph.local");
        u.setIntegrante(vigilante);
        when(usuarios.buscarConIntegrante("vigilante@ph.local")).thenReturn(Optional.of(u));

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("vigilante@ph.local");

        // save() devuelve el mismo movimiento y le asigna un número, como haría la base de datos.
        when(movimientos.save(any(MovimientoAcceso.class))).thenAnswer(inv -> {
            MovimientoAcceso m = inv.getArgument(0);
            if (m.getId() == null) {
                ReflectionTestUtils.setField(m, "id", 77L);
            }
            return m;
        });
    }

    // ---------- Ingreso con autorización (RF-033, RF-044, RN-09) ----------

    @Test
    void ingresoDeUnaVisitaOcasionalRegistraElMovimientoYGastaLaAutorizacion() {
        Autorizacion a = ocasional(5L);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        AccesoDto dto = controller.ingreso(new IngresoRequest(5L, "  abc123 "), auth);

        assertEquals(77L, dto.id());
        assertEquals("OCASIONAL", dto.tipo());
        assertEquals("Juan Perez", dto.nombre());
        assertEquals("123", dto.documento());
        assertEquals("Apto 101", dto.inmueble());
        assertEquals("ABC123", dto.placa());
        assertEquals("Victor Vigia", dto.vigilanteIngreso());
        assertNull(dto.vigilanteSalida());
        assertTrue(dto.dentro());
        assertNotNull(dto.ingresoEn());

        // La visita ocasional se gasta con el primer ingreso.
        assertEquals(EstadoAutorizacion.UTILIZADA, a.getEstado());
        verify(autorizaciones).save(a);
        verify(auditoria).registrar(eq("INGRESAR"), eq("Acceso"), eq(77L), eq("Inmueble Apto 101"));
        verify(notificaciones).avisar(eq(EventoNotificacion.VISITANTE_INGRESO), eq(apto101),
                anyString(), anyString());
    }

    @Test
    void ingresoDeUnAutorizadoRecurrenteNoGastaLaAutorizacionYAvisaConOtroEvento() {
        Autorizacion a = recurrente(6L, nombreDia(LocalDate.now().getDayOfWeek()), LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));

        AccesoDto dto = controller.ingreso(new IngresoRequest(6L, null), auth);

        assertEquals("RECURRENTE", dto.tipo());
        assertNull(dto.placa());
        assertEquals(EstadoAutorizacion.VIGENTE, a.getEstado());
        verify(autorizaciones, never()).save(any());
        verify(notificaciones).avisar(eq(EventoNotificacion.AUTORIZACION_RECURRENTE), eq(apto101),
                anyString(), anyString());
    }

    @Test
    void ingresoGuardaElMovimientoConLosDatosDeLaAutorizacion() {
        Autorizacion a = ocasional(5L);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        controller.ingreso(new IngresoRequest(5L, ""), auth);

        ArgumentCaptor<MovimientoAcceso> captor = ArgumentCaptor.forClass(MovimientoAcceso.class);
        verify(movimientos).save(captor.capture());
        MovimientoAcceso m = captor.getValue();
        assertSame(a, m.getAutorizacion());
        assertSame(apto101, m.getInmueble());
        assertEquals("Juan Perez", m.getNombre());
        assertEquals("123", m.getDocumento());
        assertNull(m.getPlaca()); // placa vacía se guarda como ausente
        assertNull(m.getSalidaEn());
        assertSame(vigilante, m.getVigilanteIngreso());
    }

    @Test
    void ingresoOcasionalIgnoraDiaYFranjaHoraria() {
        Autorizacion a = ocasional(5L);
        a.setDiasSemana(null);
        a.setHoraInicio(null);
        a.setHoraFin(null);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        assertEquals("OCASIONAL", controller.ingreso(new IngresoRequest(5L, null), auth).tipo());
    }

    @Test
    void ingresoExigeAutorizacionYRechazaUnaInexistente() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingreso(new IngresoRequest(null, "ABC123"), auth));
        when(autorizaciones.findById(404L)).thenReturn(Optional.empty());
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.ingreso(new IngresoRequest(404L, null), auth));
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoRechazaAutorizacionesQueNoEstanVigentes() {
        for (EstadoAutorizacion e : new EstadoAutorizacion[] {EstadoAutorizacion.UTILIZADA,
                EstadoAutorizacion.CANCELADA, EstadoAutorizacion.REVOCADA}) {
            Autorizacion a = ocasional(5L);
            a.setEstado(e);
            when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

            ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                    () -> controller.ingreso(new IngresoRequest(5L, null), auth));
            assertTrue(ex.getReason().contains(e.name().toLowerCase()), ex.getReason());
        }
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoRechazaUnaAutorizacionVencidaSinModificarla() {
        // RN-09: pierde validez sola al vencer su vigencia; la fila sigue como VIGENTE.
        Autorizacion a = ocasional(5L);
        a.setFechaInicio(LocalDate.now().minusDays(10));
        a.setFechaFin(LocalDate.now().minusDays(1));
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(5L, null), auth));

        assertTrue(ex.getReason().contains("vencida"));
        assertEquals(EstadoAutorizacion.VIGENTE, a.getEstado());
    }

    @Test
    void ingresoRechazaSiElInmuebleEstaDesactivado() {
        Autorizacion a = ocasional(5L);
        apto101.setActivo(false);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(5L, null), auth));

        assertTrue(ex.getReason().contains("desactivado"));
    }

    @Test
    void ingresoRechazaSiLaVigenciaTodaviaNoEmpieza() {
        Autorizacion a = ocasional(5L);
        a.setFechaInicio(LocalDate.now().plusDays(2));
        a.setFechaFin(LocalDate.now().plusDays(5));
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(5L, null), auth));

        assertTrue(ex.getReason().contains("Todavía no empieza"));
    }

    @Test
    void ingresoRecurrenteRechazaUnDiaNoPermitido() {
        String manana = nombreDia(LocalDate.now().plusDays(1).getDayOfWeek());
        Autorizacion a = recurrente(6L, manana, LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(6L, null), auth));

        assertTrue(ex.getReason().contains("Hoy no es un día permitido"));
        assertTrue(ex.getReason().contains(manana));
    }

    @Test
    void ingresoRecurrenteSinDiasConfiguradosSeRechaza() {
        Autorizacion a = recurrente(6L, null, LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));

        assertEstado(HttpStatus.CONFLICT, () -> controller.ingreso(new IngresoRequest(6L, null), auth));
    }

    @Test
    void ingresoRecurrenteAceptaVariosDiasSiHoyEstaEntreEllos() {
        String hoy = nombreDia(LocalDate.now().getDayOfWeek());
        String otro = nombreDia(LocalDate.now().plusDays(3).getDayOfWeek());
        Autorizacion a = recurrente(6L, otro + "," + hoy, LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));

        assertEquals("RECURRENTE", controller.ingreso(new IngresoRequest(6L, null), auth).tipo());
    }

    @Test
    void ingresoRecurrenteRechazaFueraDeLaFranjaHoraria() {
        LocalTime inicio = franjaLejanaAAhora();
        Autorizacion a = recurrente(6L, nombreDia(LocalDate.now().getDayOfWeek()), inicio, inicio.plusMinutes(1));
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(6L, null), auth));

        assertTrue(ex.getReason().contains("Fuera de la franja horaria"));
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoRechazaSiLaPersonaYaEstaDentro() {
        Autorizacion a = recurrente(6L, nombreDia(LocalDate.now().getDayOfWeek()), LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findById(6L)).thenReturn(Optional.of(a));
        when(movimientos.existsByAutorizacionIdAndSalidaEnIsNull(6L)).thenReturn(true);

        ResponseStatusException ex = fallo(HttpStatus.CONFLICT,
                () -> controller.ingreso(new IngresoRequest(6L, null), auth));

        assertTrue(ex.getReason().contains("ya se encuentra dentro"));
    }

    @Test
    void ingresoRechazaPlacaMuyLargaYCuentaInvalidaSinGastarLaAutorizacion() {
        Autorizacion a = ocasional(5L);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingreso(new IngresoRequest(5L, "A".repeat(11)), auth));
        // Con una cuenta inválida tampoco se registra nada.
        when(usuarios.buscarConIntegrante("vigilante@ph.local")).thenReturn(Optional.empty());
        assertEstado(HttpStatus.FORBIDDEN, () -> controller.ingreso(new IngresoRequest(5L, "ABC123"), auth));

        assertEquals(EstadoAutorizacion.VIGENTE, a.getEstado());
        verify(movimientos, never()).save(any());
        verify(notificaciones, never()).avisar(any(), any(), any(), any());
    }

    @Test
    void ingresoAceptaUnaPlacaDeDiezCaracteres() {
        Autorizacion a = ocasional(5L);
        when(autorizaciones.findById(5L)).thenReturn(Optional.of(a));

        assertEquals("ABCDE12345", controller.ingreso(new IngresoRequest(5L, "abcde12345"), auth).placa());
    }

    // ---------- Ingreso sin autorización (RF-035, RN-12) ----------

    @Test
    void ingresoSinAutorizacionRegistraElMotivoYNormalizaLosDatos() {
        AccesoDto dto = controller.ingresoSinAutorizacion(new SinAutorizacionRequest(
                10L, "  Maria Lopez ", " 555 ", "  Domicilio urgente ", " xyz789 "), auth);

        assertEquals("SIN_AUTORIZACION", dto.tipo());
        assertEquals("Maria Lopez", dto.nombre());
        assertEquals("555", dto.documento());
        assertEquals("Domicilio urgente", dto.motivo());
        assertEquals("XYZ789", dto.placa());
        assertEquals("Apto 101", dto.inmueble());
        assertEquals("Victor Vigia", dto.vigilanteIngreso());
        assertTrue(dto.dentro());

        ArgumentCaptor<MovimientoAcceso> captor = ArgumentCaptor.forClass(MovimientoAcceso.class);
        verify(movimientos).save(captor.capture());
        assertNull(captor.getValue().getAutorizacion());
        verify(auditoria).registrar(eq("INGRESAR_SIN_AUTORIZACION"), eq("Acceso"), eq(77L),
                eq("Inmueble Apto 101. Motivo: Domicilio urgente"));
    }

    @Test
    void ingresoSinAutorizacionExigeInmuebleNombreYDocumento() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(null, "Ana", "1", "motivo", null), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, " ", "1", "motivo", null), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, "Ana", null, "motivo", null), auth));
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoSinAutorizacionExigeMotivo() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, "Ana", "1", null, null), auth));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, "Ana", "1", "   ", null), auth));
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoSinAutorizacionRechazaInmuebleInexistenteODesactivado() {
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(999L, "Ana", "1", "motivo", null), auth));

        apto101.setActivo(false);
        assertEstado(HttpStatus.CONFLICT, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, "Ana", "1", "motivo", null), auth));
        verify(movimientos, never()).save(any());
    }

    @Test
    void ingresoSinAutorizacionRechazaPlacaMuyLarga() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.ingresoSinAutorizacion(
                new SinAutorizacionRequest(10L, "Ana", "1", "motivo", "ABCDEFGHIJK"), auth));
    }

    // ---------- Salida (RF-034) ----------

    @Test
    void salidaRegistraLaHoraYElVigilanteQueLaAtendio() {
        MovimientoAcceso m = movimiento(3L, "Juan Perez");
        when(movimientos.findById(3L)).thenReturn(Optional.of(m));

        AccesoDto dto = controller.salida(3L, auth);

        assertNotNull(m.getSalidaEn());
        assertSame(vigilante, m.getVigilanteSalida());
        assertFalse(dto.dentro());
        assertEquals("Victor Vigia", dto.vigilanteSalida());
        verify(movimientos).save(m);
        verify(auditoria).registrar(eq("SALIR"), eq("Acceso"), eq(3L), eq("Inmueble Apto 101"));
    }

    @Test
    void salidaRechazaMovimientoInexistenteOYaCerrado() {
        when(movimientos.findById(404L)).thenReturn(Optional.empty());
        assertEstado(HttpStatus.NOT_FOUND, () -> controller.salida(404L, auth));

        MovimientoAcceso cerrado = movimiento(3L, "Juan Perez");
        LocalDateTime salida = LocalDateTime.now().minusHours(1);
        cerrado.setSalidaEn(salida);
        when(movimientos.findById(3L)).thenReturn(Optional.of(cerrado));

        assertEstado(HttpStatus.CONFLICT, () -> controller.salida(3L, auth));
        assertEquals(salida, cerrado.getSalidaEn()); // no se sobrescribe
        verify(movimientos, never()).save(any());
    }

    @Test
    void salidaConCuentaInvalidaNoCierraElMovimiento() {
        MovimientoAcceso m = movimiento(3L, "Juan Perez");
        when(movimientos.findById(3L)).thenReturn(Optional.of(m));
        when(usuarios.buscarConIntegrante("vigilante@ph.local")).thenReturn(Optional.empty());

        assertEstado(HttpStatus.FORBIDDEN, () -> controller.salida(3L, auth));
        verify(movimientos, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    // ---------- Quién está dentro (RF-036) ----------

    @Test
    void dentroListaLosMovimientosSinSalida() {
        MovimientoAcceso a = movimiento(1L, "Ana");
        MovimientoAcceso b = movimiento(2L, "Beto");
        when(movimientos.findBySalidaEnIsNullOrderByIngresoEnDesc()).thenReturn(List.of(b, a));

        List<AccesoDto> lista = controller.dentro();

        assertEquals(List.of("Beto", "Ana"), lista.stream().map(AccesoDto::nombre).toList());
        assertTrue(lista.stream().allMatch(AccesoDto::dentro));
    }

    @Test
    void dentroSinNadieDevuelveListaVacia() {
        when(movimientos.findBySalidaEnIsNullOrderByIngresoEnDesc()).thenReturn(List.of());

        assertTrue(controller.dentro().isEmpty());
    }

    // ---------- Búsqueda de autorizaciones (RF-031, RF-032, RF-043) ----------

    @Test
    void buscarExigeDocumentoOInmueble() {
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.buscar(null, null));
        assertEstado(HttpStatus.BAD_REQUEST, () -> controller.buscar("   ", null));
        verify(autorizaciones, never()).findByDocumentoOrderByIdDesc(any());
        verify(autorizaciones, never()).findByInmuebleIdOrderByIdDesc(any());
    }

    @Test
    void buscarPorDocumentoTienePrioridadSobreElInmuebleYRecortaEspacios() {
        when(autorizaciones.findByDocumentoOrderByIdDesc("123")).thenReturn(List.of(ocasional(5L)));

        List<AutorizacionPorteriaDto> r = controller.buscar(" 123 ", 10L);

        assertEquals(1, r.size());
        verify(autorizaciones, never()).findByInmuebleIdOrderByIdDesc(any());
    }

    @Test
    void buscarPorInmuebleDevuelveSoloLasVigentesEfectivas() {
        Autorizacion vigente = ocasional(1L);
        Autorizacion utilizada = ocasional(2L);
        utilizada.setEstado(EstadoAutorizacion.UTILIZADA);
        Autorizacion revocada = ocasional(3L);
        revocada.setEstado(EstadoAutorizacion.REVOCADA);
        Autorizacion vencida = ocasional(4L);
        vencida.setFechaInicio(LocalDate.now().minusDays(9));
        vencida.setFechaFin(LocalDate.now().minusDays(1));
        when(autorizaciones.findByInmuebleIdOrderByIdDesc(10L))
                .thenReturn(List.of(vencida, revocada, utilizada, vigente));

        List<AutorizacionPorteriaDto> r = controller.buscar(null, 10L);

        assertEquals(List.of(1L), r.stream().map(AutorizacionPorteriaDto::id).toList());
    }

    @Test
    void buscarIndicaSiPuedeIngresarAhoraYElMotivoSiNoPuede() {
        Autorizacion puede = recurrente(1L, nombreDia(LocalDate.now().getDayOfWeek()), LocalTime.MIN, LocalTime.MAX);
        Autorizacion noPuede = recurrente(2L, nombreDia(LocalDate.now().plusDays(1).getDayOfWeek()),
                LocalTime.MIN, LocalTime.MAX);
        when(autorizaciones.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(noPuede, puede));

        List<AutorizacionPorteriaDto> r = controller.buscar(null, 10L);

        AutorizacionPorteriaDto si = r.stream().filter(d -> d.id() == 1L).findFirst().orElseThrow();
        AutorizacionPorteriaDto no = r.stream().filter(d -> d.id() == 2L).findFirst().orElseThrow();
        assertTrue(si.puedeIngresar());
        assertNull(si.motivoRechazo());
        assertFalse(no.puedeIngresar());
        assertTrue(no.motivoRechazo().contains("Hoy no es un día permitido"));
    }

    @Test
    void buscarMarcaQueNoPuedeIngresarSiYaEstaDentro() {
        Autorizacion a = ocasional(5L);
        when(autorizaciones.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(a));
        when(movimientos.existsByAutorizacionIdAndSalidaEnIsNull(5L)).thenReturn(true);

        AutorizacionPorteriaDto d = controller.buscar(null, 10L).get(0);

        assertFalse(d.puedeIngresar());
        assertTrue(d.motivoRechazo().contains("ya se encuentra dentro"));
    }

    @Test
    void buscarSeparaLosDiasYTrataLosDiasVaciosComoListaVacia() {
        Autorizacion rec = recurrente(1L, "MARTES,JUEVES", LocalTime.of(8, 0), LocalTime.of(17, 0));
        Autorizacion oca = ocasional(2L);
        oca.setDiasSemana("  ");
        Autorizacion sinDias = ocasional(3L);
        sinDias.setDiasSemana(null);
        when(autorizaciones.findByInmuebleIdOrderByIdDesc(10L)).thenReturn(List.of(rec, oca, sinDias));

        List<AutorizacionPorteriaDto> r = controller.buscar(null, 10L);

        assertEquals(List.of("MARTES", "JUEVES"), r.get(0).dias());
        assertEquals(LocalTime.of(8, 0), r.get(0).horaInicio());
        assertTrue(r.get(1).dias().isEmpty());
        assertTrue(r.get(2).dias().isEmpty());
        assertEquals("Apto 101", r.get(0).inmueble());
    }

    // ---------- Historial (RF-037) ----------

    @Test
    void historialSinFechasUsaLosUltimosTreintaDias() {
        when(movimientos.findByIngresoEnBetweenOrderByIngresoEnDesc(any(), any()))
                .thenReturn(List.of(movimiento(1L, "Ana")));

        List<AccesoDto> r = controller.historial(null, null, null);

        assertEquals(1, r.size());
        verify(movimientos).findByIngresoEnBetweenOrderByIngresoEnDesc(
                LocalDate.now().minusDays(30).atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));
        verify(movimientos, never()).findByInmuebleIdAndIngresoEnBetweenOrderByIngresoEnDesc(any(), any(), any());
    }

    @Test
    void historialFiltraPorInmuebleYPorElRangoPedidoIncluyendoElUltimoDia() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        when(movimientos.findByInmuebleIdAndIngresoEnBetweenOrderByIngresoEnDesc(any(), any(), any()))
                .thenReturn(List.of(movimiento(1L, "Ana"), movimiento(2L, "Beto")));

        List<AccesoDto> r = controller.historial(10L, desde, hasta);

        assertEquals(2, r.size());
        verify(movimientos).findByInmuebleIdAndIngresoEnBetweenOrderByIngresoEnDesc(
                10L, desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
        verify(movimientos, never()).findByIngresoEnBetweenOrderByIngresoEnDesc(any(), any());
    }

    @Test
    void historialSoloConDesdeTerminaHoy() {
        LocalDate desde = LocalDate.now().minusDays(5);
        when(movimientos.findByIngresoEnBetweenOrderByIngresoEnDesc(any(), any())).thenReturn(List.of());

        controller.historial(null, desde, null);

        verify(movimientos).findByIngresoEnBetweenOrderByIngresoEnDesc(
                desde.atStartOfDay(), LocalDate.now().atTime(LocalTime.MAX));
    }

    @Test
    void historialSoloConHastaEmpiezaTreintaDiasAntes() {
        LocalDate hasta = LocalDate.of(2026, 6, 30);
        when(movimientos.findByIngresoEnBetweenOrderByIngresoEnDesc(any(), any())).thenReturn(List.of());

        controller.historial(null, null, hasta);

        verify(movimientos).findByIngresoEnBetweenOrderByIngresoEnDesc(
                hasta.minusDays(30).atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    // ---------- Ayudas ----------

    private void assertEstado(HttpStatus esperado, Executable accion) {
        fallo(esperado, accion);
    }

    private ResponseStatusException fallo(HttpStatus esperado, Executable accion) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, accion);
        assertEquals(esperado.value(), ex.getStatusCode().value());
        return ex;
    }

    private static void assertSame(Object esperado, Object real) {
        org.junit.jupiter.api.Assertions.assertSame(esperado, real);
    }

    private static String nombreDia(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    /** Una hora de inicio que nunca contiene a "ahora": 20:00 si es de mañana, 04:00 si es de tarde. */
    private static LocalTime franjaLejanaAAhora() {
        return LocalTime.now().getHour() < 12 ? LocalTime.of(20, 0) : LocalTime.of(4, 0);
    }

    private Inmueble inmueble(long id, String identificacion) {
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id);
        i.setIdentificacion(identificacion);
        return i;
    }

    private Autorizacion ocasional(long id) {
        Autorizacion a = base(id, TipoAutorizacion.OCASIONAL);
        return a;
    }

    private Autorizacion recurrente(long id, String dias, LocalTime inicio, LocalTime fin) {
        Autorizacion a = base(id, TipoAutorizacion.RECURRENTE);
        a.setDiasSemana(dias);
        a.setHoraInicio(inicio);
        a.setHoraFin(fin);
        return a;
    }

    private Autorizacion base(long id, TipoAutorizacion tipo) {
        Autorizacion a = new Autorizacion();
        ReflectionTestUtils.setField(a, "id", id);
        a.setInmueble(apto101);
        a.setTipo(tipo);
        a.setNombre("Juan Perez");
        a.setDocumento("123");
        a.setFechaInicio(LocalDate.now().minusDays(1));
        a.setFechaFin(LocalDate.now().plusDays(7));
        return a;
    }

    private MovimientoAcceso movimiento(long id, String nombre) {
        MovimientoAcceso m = new MovimientoAcceso();
        ReflectionTestUtils.setField(m, "id", id);
        m.setInmueble(apto101);
        m.setNombre(nombre);
        m.setDocumento("999");
        m.setIngresoEn(LocalDateTime.now().minusHours(2));
        m.setVigilanteIngreso(vigilante);
        return m;
    }
}
