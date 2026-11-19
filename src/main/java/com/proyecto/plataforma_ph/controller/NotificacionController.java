package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.ConfiguracionNotificacion;
import com.proyecto.plataforma_ph.model.DestinatarioNotificacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Notificacion;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.NotificacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-17: configurar notificaciones (administrador) y bandeja de avisos de los residentes.
 *
 * Administrador:
 *   GET   /api/notificaciones/config
 *   PUT   /api/notificaciones/config/{evento}    {destinatario, habilitado}
 *   GET   /api/notificaciones?evento=            (registro de envíos)
 *   POST  /api/notificaciones/probar             {evento, inmuebleId}
 * Residentes:
 *   GET   /api/notificaciones/mias
 *   PATCH /api/notificaciones/{id}/leida
 *   POST  /api/notificaciones/mias/leer-todas
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record ConfigDto(String evento, String etiqueta, String grupo, String descripcion, boolean cobro,
                            String destinatario, String destinatarioEtiqueta, boolean habilitado) {}

    public record CambioConfig(String destinatario, Boolean habilitado) {}

    public record NotificacionDto(Long id, String evento, String eventoEtiqueta, String destinatario,
                                  String inmueble, String asunto, String mensaje, String resultado,
                                  String resultadoEtiqueta, String fecha, boolean leida) {}

    public record Prueba(String evento, Long inmuebleId) {}

    private final NotificacionService servicio;
    private final NotificacionRepository notificaciones;
    private final InmuebleRepository inmuebles;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public NotificacionController(NotificacionService servicio, NotificacionRepository notificaciones,
                                  InmuebleRepository inmuebles, UsuarioRepository usuarios,
                                  AuditoriaService auditoria) {
        this.servicio = servicio;
        this.notificaciones = notificaciones;
        this.inmuebles = inmuebles;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // =====================================================================
    // Administrador (RF-107)
    // =====================================================================

    @GetMapping("/config")
    @Transactional
    public List<ConfigDto> configuracion() {
        List<ConfigDto> lista = new ArrayList<>();
        for (EventoNotificacion e : EventoNotificacion.values()) {
            lista.add(configDto(servicio.configuracion(e)));
        }
        return lista;
    }

    @PutMapping("/config/{evento}")
    @Transactional
    public ConfigDto configurar(@PathVariable String evento, @RequestBody CambioConfig body) {
        EventoNotificacion ev = evento(evento);
        ConfiguracionNotificacion cfg = servicio.configuracion(ev);

        if (body.destinatario() != null) {
            DestinatarioNotificacion d = destinatario(body.destinatario());
            // RN-28: un aviso de cobro siempre debe llegar al obligado del pago.
            if (ev.isCobro() && d == DestinatarioNotificacion.RESIDENTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Un aviso de cobro debe llegar al propietario obligado del pago");
            }
            cfg.setDestinatario(d);
        }
        if (body.habilitado() != null) {
            cfg.setHabilitado(body.habilitado());
        }

        auditoria.registrar("CONFIGURAR_NOTIFICACION", "ConfiguracionNotificacion", cfg.getId(),
                ev.name() + " -> " + cfg.getDestinatario().name()
                        + (cfg.isHabilitado() ? " (habilitado)" : " (deshabilitado)"));
        return configDto(cfg);
    }

    // Registro de envíos.
    @GetMapping
    @Transactional(readOnly = true)
    public List<NotificacionDto> registro(@RequestParam(required = false) String evento) {
        List<Notificacion> lista = (evento == null || evento.isBlank())
                ? notificaciones.findTop200ByOrderByIdDesc()
                : notificaciones.findTop200ByEventoOrderByIdDesc(evento(evento));
        return lista.stream().map(this::dto).toList();
    }

    // Envía un aviso de prueba para comprobar la configuración.
    @PostMapping("/probar")
    @Transactional
    public List<NotificacionDto> probar(@RequestBody Prueba body) {
        if (body.evento() == null || body.inmuebleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elija el evento y el inmueble");
        }
        EventoNotificacion ev = evento(body.evento());
        Inmueble inmueble = inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        if (!servicio.configuracion(ev).isHabilitado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese evento está deshabilitado");
        }
        List<Notificacion> creadas = servicio.notificar(ev, inmueble,
                "Prueba: " + ev.getEtiqueta(),
                "Este es un aviso de prueba enviado por la administración (" + ev.getEtiqueta()
                        + ") para el inmueble " + inmueble.getIdentificacion() + ".");
        auditoria.registrar("PROBAR_NOTIFICACION", "Inmueble", inmueble.getId(),
                ev.name() + ", avisos generados: " + creadas.size());
        return creadas.stream().map(this::dto).toList();
    }

    // =====================================================================
    // Residentes
    // =====================================================================

    @GetMapping("/mias")
    @Transactional(readOnly = true)
    public List<NotificacionDto> mias(Authentication auth) {
        Integrante yo = integranteActual(auth);
        return notificaciones.findTop100ByDestinatarioIdOrderByIdDesc(yo.getId()).stream()
                .map(this::dto).toList();
    }

    @PatchMapping("/{id}/leida")
    @Transactional
    public NotificacionDto leida(@PathVariable Long id, Authentication auth) {
        Integrante yo = integranteActual(auth);
        Notificacion n = notificaciones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada"));
        if (n.getDestinatario() == null || !n.getDestinatario().getId().equals(yo.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa notificación no es suya");
        }
        if (!n.isLeida()) {
            n.setLeida(true);
            n.setLeidaEn(LocalDateTime.now());
        }
        return dto(n);
    }

    @PostMapping("/mias/leer-todas")
    @Transactional
    public int leerTodas(Authentication auth) {
        Integrante yo = integranteActual(auth);
        List<Notificacion> pendientes = notificaciones.findByDestinatarioIdAndLeidaFalse(yo.getId());
        LocalDateTime ahora = LocalDateTime.now();
        for (Notificacion n : pendientes) {
            n.setLeida(true);
            n.setLeidaEn(ahora);
        }
        return pendientes.size();
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    private EventoNotificacion evento(String texto) {
        try {
            return EventoNotificacion.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento no válido");
        }
    }

    private DestinatarioNotificacion destinatario(String texto) {
        try {
            return DestinatarioNotificacion.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destinatario no válido");
        }
    }

    private ConfigDto configDto(ConfiguracionNotificacion c) {
        EventoNotificacion e = c.getEvento();
        return new ConfigDto(e.name(), e.getEtiqueta(), e.getGrupo(), e.getDescripcion(), e.isCobro(),
                c.getDestinatario().name(), c.getDestinatario().getEtiqueta(), c.isHabilitado());
    }

    private NotificacionDto dto(Notificacion n) {
        Integrante d = n.getDestinatario();
        return new NotificacionDto(n.getId(), n.getEvento().name(), n.getEvento().getEtiqueta(),
                d == null ? null : d.getNombres() + " " + d.getApellidos(),
                n.getInmueble() == null ? null : n.getInmueble().getIdentificacion(),
                n.getAsunto(), n.getMensaje(), n.getResultado().name(), n.getResultado().getEtiqueta(),
                n.getCreadaEn().format(FECHA), n.isLeida());
    }
}