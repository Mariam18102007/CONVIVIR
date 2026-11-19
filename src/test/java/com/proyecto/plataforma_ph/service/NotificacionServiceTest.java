package com.proyecto.plataforma_ph.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.model.ConfiguracionNotificacion;
import com.proyecto.plataforma_ph.model.DestinatarioNotificacion;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Notificacion;
import com.proyecto.plataforma_ph.model.ResultadoNotificacion;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.ConfiguracionNotificacionRepository;
import com.proyecto.plataforma_ph.repository.NotificacionRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Notificaciones (CU-17): RN-28 (el aviso de cobro va al obligado del pago),
 * RN-29 (los avisos operativos van a residentes y arrendatarios) y
 * RN-30 (el envío registra su resultado).
 */
class NotificacionServiceTest {

    private ConfiguracionNotificacionRepository configuraciones;
    private NotificacionRepository notificaciones;
    private VinculacionRepository vinculaciones;
    private NotificacionService servicio;
    private Inmueble inmueble;

    @BeforeEach
    void preparar() {
        configuraciones = mock(ConfiguracionNotificacionRepository.class);
        notificaciones = mock(NotificacionRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        servicio = new NotificacionService(configuraciones, notificaciones, vinculaciones);

        inmueble = new Inmueble();
        ReflectionTestUtils.setField(inmueble, "id", 10L);
        inmueble.setIdentificacion("Apto 101");

        // Sin configuración guardada: se crea con los valores por defecto.
        when(configuraciones.findByEvento(any())).thenReturn(Optional.empty());
        when(configuraciones.save(any(ConfiguracionNotificacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificaciones.save(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- ayudas ----------

    private Integrante persona(long id) {
        Integrante i = new Integrante();
        ReflectionTestUtils.setField(i, "id", id);
        i.setNombres("Persona" + id);
        i.setApellidos("Prueba");
        return i;
    }

    private Vinculacion vinculo(Integrante de, TipoVinculo tipo) {
        Vinculacion v = new Vinculacion();
        v.setIntegrante(de);
        v.setInmueble(inmueble);
        v.setTipoVinculo(tipo);
        v.setEstado(EstadoVinculacion.VIGENTE);
        return v;
    }

    private void vigentes(Vinculacion... lista) {
        when(vinculaciones.findByInmuebleIdAndEstado(10L, EstadoVinculacion.VIGENTE)).thenReturn(List.of(lista));
    }

    private ConfiguracionNotificacion configuracion(EventoNotificacion evento, DestinatarioNotificacion destino,
                                                    boolean habilitado) {
        ConfiguracionNotificacion c = new ConfiguracionNotificacion();
        c.setEvento(evento);
        c.setDestinatario(destino);
        c.setHabilitado(habilitado);
        when(configuraciones.findByEvento(evento)).thenReturn(Optional.of(c));
        return c;
    }

    private List<Long> ids(List<Notificacion> enviadas) {
        return enviadas.stream().map(n -> n.getDestinatario().getId()).toList();
    }

    // ---------- configuración por defecto ----------

    @Test
    void sinConfiguracionGuardadaSeCreaConLosValoresPorDefecto() {
        ConfiguracionNotificacion cobro = servicio.configuracion(EventoNotificacion.PAGO_CONFIRMADO);
        ConfiguracionNotificacion operativo = servicio.configuracion(EventoNotificacion.REPORTE_ACTUALIZADO);

        assertEquals(DestinatarioNotificacion.OBLIGADO_PAGO, cobro.getDestinatario());
        assertEquals(DestinatarioNotificacion.RESIDENTES, operativo.getDestinatario());
        assertTrue(cobro.isHabilitado());
        assertTrue(operativo.isHabilitado());
    }

    // ---------- RN-28: avisos de cobro ----------

    @Test
    void elAvisoDeCobroLlegaAlPropietarioYNoAlArrendatario() {
        vigentes(vinculo(persona(1), TipoVinculo.PROPIETARIO_NO_RESIDENTE),
                vinculo(persona(2), TipoVinculo.ARRENDATARIO));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble,
                "Pago", "Se confirmó su pago");

        assertEquals(List.of(1L), ids(enviadas));
        assertEquals(ResultadoNotificacion.ENTREGADA, enviadas.get(0).getResultado());
    }

    // ---------- RN-29: avisos operativos ----------

    @Test
    void elAvisoOperativoLlegaAPropietarioResidenteYArrendatarioPeroNoAOtrosVinculos() {
        vigentes(vinculo(persona(1), TipoVinculo.PROPIETARIO_RESIDENTE),
                vinculo(persona(2), TipoVinculo.ARRENDATARIO),
                vinculo(persona(3), TipoVinculo.PROPIETARIO_NO_RESIDENTE),
                vinculo(persona(4), TipoVinculo.RESIDENTE_AUTORIZADO));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.REPORTE_ACTUALIZADO, inmueble,
                "Reporte", "Cambió el estado");

        assertEquals(List.of(1L, 2L), ids(enviadas));
    }

    @Test
    void conTodosLosVinculadosLeLlegaATodosLosVigentes() {
        configuracion(EventoNotificacion.VISITANTE_INGRESO, DestinatarioNotificacion.TODOS_VINCULADOS, true);
        vigentes(vinculo(persona(1), TipoVinculo.PROPIETARIO_RESIDENTE),
                vinculo(persona(2), TipoVinculo.ARRENDATARIO),
                vinculo(persona(3), TipoVinculo.PROPIETARIO_NO_RESIDENTE),
                vinculo(persona(4), TipoVinculo.RESIDENTE_AUTORIZADO));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.VISITANTE_INGRESO, inmueble,
                "Visita", "Llegó una visita");

        assertEquals(List.of(1L, 2L, 3L, 4L), ids(enviadas));
    }

    // ---------- RF-107: habilitar o deshabilitar ----------

    @Test
    void unEventoDeshabilitadoNoGeneraAvisos() {
        configuracion(EventoNotificacion.PAGO_CONFIRMADO, DestinatarioNotificacion.OBLIGADO_PAGO, false);
        vigentes(vinculo(persona(1), TipoVinculo.PROPIETARIO_RESIDENTE));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble,
                "Pago", "Mensaje");

        assertTrue(enviadas.isEmpty());
        verify(notificaciones, never()).save(any(Notificacion.class));
    }

    // ---------- RN-30: resultado del envío ----------

    @Test
    void sinNadieQueLoReciba_queDaRegistradoSinDestinatario() {
        vigentes(vinculo(persona(2), TipoVinculo.ARRENDATARIO)); // un cobro no es para el arrendatario

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble,
                "Pago", "Mensaje");

        assertEquals(1, enviadas.size());
        assertEquals(ResultadoNotificacion.SIN_DESTINATARIO, enviadas.get(0).getResultado());
        assertNull(enviadas.get(0).getDestinatario());
    }

    @Test
    void unIntegranteDesactivadoNoRecibeAvisos() {
        Integrante inactivo = persona(1);
        inactivo.setActivo(false);
        vigentes(vinculo(inactivo, TipoVinculo.PROPIETARIO_RESIDENTE));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble,
                "Pago", "Mensaje");

        assertEquals(ResultadoNotificacion.SIN_DESTINATARIO, enviadas.get(0).getResultado());
    }

    @Test
    void quienTieneDosVinculosRecibeUnSoloAviso() {
        Integrante mismo = persona(1);
        vigentes(vinculo(mismo, TipoVinculo.PROPIETARIO_RESIDENTE),
                vinculo(mismo, TipoVinculo.ARRENDATARIO));

        List<Notificacion> enviadas = servicio.notificar(EventoNotificacion.REPORTE_ACTUALIZADO, inmueble,
                "Reporte", "Mensaje");

        assertEquals(1, enviadas.size());
    }

    @Test
    void elMensajeLargoSeRecortaAlLimiteDeLaColumna() {
        vigentes(vinculo(persona(1), TipoVinculo.PROPIETARIO_RESIDENTE));
        String largo = "x".repeat(900);

        Notificacion n = servicio.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble, largo, largo).get(0);

        assertEquals(150, n.getAsunto().length());
        assertEquals(500, n.getMensaje().length());
    }

    // ---------- avisar: un fallo no afecta a quien llama ----------

    @Test
    void avisarNuncaPropagaUnFallo() {
        when(vinculaciones.findByInmuebleIdAndEstado(10L, EstadoVinculacion.VIGENTE))
                .thenThrow(new RuntimeException("falla simulada"));

        assertDoesNotThrow(() -> servicio.avisar(EventoNotificacion.PAGO_CONFIRMADO, inmueble, "Pago", "Mensaje"));
    }
}