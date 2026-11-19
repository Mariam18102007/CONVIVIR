package com.proyecto.plataforma_ph.service;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envía las notificaciones (CU-17, RN-28 a RN-30).
 * Por ahora el canal es la bandeja de la propia plataforma; el correo se puede
 * agregar después sin cambiar quién llama a este servicio (RNF-13).
 *
 * Para avisar un evento desde otro módulo:
 *   notificaciones.notificar(EventoNotificacion.PAGO_CONFIRMADO, inmueble, "Asunto", "Mensaje");
 */
@Service
public class NotificacionService {

    private final ConfiguracionNotificacionRepository configuraciones;
    private final NotificacionRepository notificaciones;
    private final VinculacionRepository vinculaciones;

    public NotificacionService(ConfiguracionNotificacionRepository configuraciones,
                               NotificacionRepository notificaciones,
                               VinculacionRepository vinculaciones) {
        this.configuraciones = configuraciones;
        this.notificaciones = notificaciones;
        this.vinculaciones = vinculaciones;
    }

    /** La configuración del evento; si no existe todavía, se crea con los valores por defecto. */
    @Transactional
    public ConfiguracionNotificacion configuracion(EventoNotificacion evento) {
        return configuraciones.findByEvento(evento).orElseGet(() -> {
            ConfiguracionNotificacion c = new ConfiguracionNotificacion();
            c.setEvento(evento);
            c.setDestinatario(evento.getPorDefecto());
            c.setHabilitado(true);
            return configuraciones.save(c);
        });
    }

    /**
     * Genera los avisos del evento para el inmueble.
     * Devuelve lista vacía si el evento está deshabilitado (RF-107).
     * Si nadie recibe el aviso, deja un registro con resultado SIN_DESTINATARIO (RN-30).
     */
    @Transactional
    public List<Notificacion> notificar(EventoNotificacion evento, Inmueble inmueble, String asunto, String mensaje) {
        ConfiguracionNotificacion cfg = configuracion(evento);
        if (!cfg.isHabilitado()) {
            return List.of();
        }

        Map<Long, Integrante> destinos = new LinkedHashMap<>();
        for (Vinculacion v : vinculaciones.findByInmuebleIdAndEstado(inmueble.getId(), EstadoVinculacion.VIGENTE)) {
            Integrante i = v.getIntegrante();
            if (i != null && i.isActivo() && aplica(cfg.getDestinatario(), v.getTipoVinculo())) {
                destinos.putIfAbsent(i.getId(), i);
            }
        }

        List<Notificacion> creadas = new ArrayList<>();
        if (destinos.isEmpty()) {
            creadas.add(notificaciones.save(armar(evento, inmueble, null, asunto, mensaje,
                    ResultadoNotificacion.SIN_DESTINATARIO)));
        } else {
            for (Integrante i : destinos.values()) {
                creadas.add(notificaciones.save(armar(evento, inmueble, i, asunto, mensaje,
                        ResultadoNotificacion.ENTREGADA)));
            }
        }
        return creadas;
    }

    private boolean aplica(DestinatarioNotificacion destino, TipoVinculo tipo) {
        return switch (destino) {
            // RN-28: el obligado del pago es el propietario.
            case OBLIGADO_PAGO -> tipo == TipoVinculo.PROPIETARIO_RESIDENTE
                    || tipo == TipoVinculo.PROPIETARIO_NO_RESIDENTE;
            // RN-29: propietarios residentes y arrendatarios.
            case RESIDENTES -> tipo == TipoVinculo.PROPIETARIO_RESIDENTE
                    || tipo == TipoVinculo.ARRENDATARIO;
            case TODOS_VINCULADOS -> true;
        };
    }

    private Notificacion armar(EventoNotificacion evento, Inmueble inmueble, Integrante destinatario,
                               String asunto, String mensaje, ResultadoNotificacion resultado) {
        Notificacion n = new Notificacion();
        n.setEvento(evento);
        n.setInmueble(inmueble);
        n.setDestinatario(destinatario);
        n.setAsunto(recortar(asunto, 150));
        n.setMensaje(recortar(mensaje, 500));
        n.setResultado(resultado);
        return n;
    }

    private String recortar(String texto, int max) {
        String t = texto == null ? "" : texto.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}