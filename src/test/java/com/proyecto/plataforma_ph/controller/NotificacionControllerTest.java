package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.NotificacionController.CambioConfig;
import com.proyecto.plataforma_ph.controller.NotificacionController.ConfigDto;
import com.proyecto.plataforma_ph.controller.NotificacionController.NotificacionDto;
import com.proyecto.plataforma_ph.controller.NotificacionController.Prueba;
import com.proyecto.plataforma_ph.model.ConfiguracionNotificacion;
import com.proyecto.plataforma_ph.model.DestinatarioNotificacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Notificacion;
import com.proyecto.plataforma_ph.model.ResultadoNotificacion;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.NotificacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Configuración de notificaciones (RF-107) y bandeja del residente.
 * RN-28: un aviso de cobro no puede dejar de llegar al propietario.
 */
class NotificacionControllerTest {

    private NotificacionService servicio;
    private NotificacionRepository notificaciones;
    private InmuebleRepository inmuebles;
    private UsuarioRepository usuarios;
    private AuditoriaService auditoria;
    private NotificacionController controller;
    private Authentication auth;
    private Integrante yo;

    @BeforeEach
    void preparar() {
        servicio = mock(NotificacionService.class);
        notificaciones = mock(NotificacionRepository.class);
        inmuebles = mock(InmuebleRepository.class);
        usuarios = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        controller = new NotificacionController(servicio, notificaciones, inmuebles, usuarios, auditoria);

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
    }

    // ---------- ayudas ----------

    private ConfiguracionNotificacion config(EventoNotificacion evento, DestinatarioNotificacion destino,
                                             boolean habilitado) {
        ConfiguracionNotificacion c = new ConfiguracionNotificacion();
        ReflectionTestUtils.setField(c, "id", 1L);
        c.setEvento(evento);
        c.setDestinatario(destino);
        c.setHabilitado(habilitado);
        when(servicio.configuracion(evento)).thenReturn(c);
        return c;
    }

    private Notificacion aviso(long id, Integrante para) {
        Notificacion n = new Notificacion();
        ReflectionTestUtils.setField(n, "id", id);
        n.setEvento(EventoNotificacion.PAGO_CONFIRMADO);
        n.setDestinatario(para);
        n.setAsunto("Pago confirmado");
        n.setMensaje("Se confirmó su pago");
        n.setResultado(ResultadoNotificacion.ENTREGADA);
        return n;
    }

    private HttpStatus estado(Runnable accion) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, accion::run);
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ---------- RF-107: configurar ----------

    @Test
    void elAvisoDeCobroNoSePuedeDirigirSoloALosResidentes() {
        config(EventoNotificacion.PAGO_CONFIRMADO, DestinatarioNotificacion.OBLIGADO_PAGO, true);

        HttpStatus e = estado(() -> controller.configurar("PAGO_CONFIRMADO",
                new CambioConfig("RESIDENTES", null)));

        assertEquals(HttpStatus.BAD_REQUEST, e);
        verify(auditoria, never()).registrar(anyString(), anyString(), any(), anyString());
    }

    @Test
    void elAvisoDeCobroSePuedeDirigirATodosLosVinculados() {
        ConfiguracionNotificacion c = config(EventoNotificacion.PAGO_CONFIRMADO,
                DestinatarioNotificacion.OBLIGADO_PAGO, true);

        ConfigDto r = controller.configurar("PAGO_CONFIRMADO", new CambioConfig("TODOS_VINCULADOS", null));

        assertEquals("TODOS_VINCULADOS", r.destinatario());
        assertEquals(DestinatarioNotificacion.TODOS_VINCULADOS, c.getDestinatario());
        verify(auditoria).registrar(anyString(), anyString(), any(), anyString());
    }

    @Test
    void elAvisoOperativoSePuedeDirigirALosResidentes() {
        config(EventoNotificacion.VISITANTE_INGRESO, DestinatarioNotificacion.TODOS_VINCULADOS, true);

        ConfigDto r = controller.configurar("VISITANTE_INGRESO", new CambioConfig("RESIDENTES", null));

        assertEquals("RESIDENTES", r.destinatario());
    }

    @Test
    void sePuedeDeshabilitarYHabilitarUnEvento() {
        ConfiguracionNotificacion c = config(EventoNotificacion.PAGO_CONFIRMADO,
                DestinatarioNotificacion.OBLIGADO_PAGO, true);

        controller.configurar("PAGO_CONFIRMADO", new CambioConfig(null, false));
        assertFalse(c.isHabilitado());

        controller.configurar("PAGO_CONFIRMADO", new CambioConfig(null, true));
        assertTrue(c.isHabilitado());
    }

    @Test
    void unEventoOUnDestinatarioInvalidoSeRechaza() {
        config(EventoNotificacion.PAGO_CONFIRMADO, DestinatarioNotificacion.OBLIGADO_PAGO, true);

        assertEquals(HttpStatus.BAD_REQUEST,
                estado(() -> controller.configurar("NO_EXISTE", new CambioConfig("RESIDENTES", null))));
        assertEquals(HttpStatus.BAD_REQUEST,
                estado(() -> controller.configurar("PAGO_CONFIRMADO", new CambioConfig("NADIE", null))));
    }

    // ---------- Probar ----------

    @Test
    void noSePuedeProbarUnEventoDeshabilitado() {
        config(EventoNotificacion.PAGO_CONFIRMADO, DestinatarioNotificacion.OBLIGADO_PAGO, false);
        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", 10L);
        when(inmuebles.findById(10L)).thenReturn(Optional.of(i));

        HttpStatus e = estado(() -> controller.probar(new Prueba("PAGO_CONFIRMADO", 10L)));

        assertEquals(HttpStatus.CONFLICT, e);
    }

    @Test
    void probarExigeEventoEInmuebleExistente() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.probar(new Prueba(null, 10L))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(() -> controller.probar(new Prueba("PAGO_CONFIRMADO", null))));

        when(inmuebles.findById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, estado(() -> controller.probar(new Prueba("PAGO_CONFIRMADO", 99L))));
    }

    // ---------- Bandeja del residente ----------

    @Test
    void miBandejaSoloTraeMisAvisos() {
        when(notificaciones.findTop100ByDestinatarioIdOrderByIdDesc(5L)).thenReturn(List.of(aviso(1L, yo)));

        List<NotificacionDto> lista = controller.mias(auth);

        assertEquals(1, lista.size());
        assertFalse(lista.get(0).leida());
    }

    @Test
    void puedoMarcarComoLeidaUnAvisoMio() {
        Notificacion n = aviso(1L, yo);
        when(notificaciones.findById(1L)).thenReturn(Optional.of(n));

        NotificacionDto r = controller.leida(1L, auth);

        assertTrue(r.leida());
        assertTrue(n.isLeida());
    }

    @Test
    void noPuedoMarcarUnAvisoDeOtraPersona() {
        Integrante otro = new Integrante();
        ReflectionTestUtils.setField(otro, "id", 77L);
        otro.setNombres("Otro");
        otro.setApellidos("Vecino");
        Notificacion ajena = aviso(2L, otro);
        when(notificaciones.findById(2L)).thenReturn(Optional.of(ajena));

        HttpStatus e = estado(() -> controller.leida(2L, auth));

        assertEquals(HttpStatus.FORBIDDEN, e);
        assertFalse(ajena.isLeida());
    }

    @Test
    void marcarUnAvisoInexistenteDaNoEncontrado() {
        when(notificaciones.findById(404L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, estado(() -> controller.leida(404L, auth)));
    }

    @Test
    void leerTodasMarcaMisPendientesYDevuelveCuantas() {
        Notificacion a = aviso(1L, yo);
        Notificacion b = aviso(2L, yo);
        when(notificaciones.findByDestinatarioIdAndLeidaFalse(5L)).thenReturn(List.of(a, b));

        int total = controller.leerTodas(auth);

        assertEquals(2, total);
        assertTrue(a.isLeida());
        assertTrue(b.isLeida());
    }
}
