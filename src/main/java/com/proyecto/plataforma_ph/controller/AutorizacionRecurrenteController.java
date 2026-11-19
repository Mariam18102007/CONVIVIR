package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.AutorizacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.NotificacionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// CU-06: autorizaciones recurrentes (personal de servicio, cuidadores, contratistas...).
@RestController
@RequestMapping("/api/autorizaciones")
public class AutorizacionRecurrenteController {

    private static final List<String> DIAS =
            List.of("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO");

    public record RecurrenteRequest(Long inmuebleId, String nombre, String documento, String motivo,
                                    LocalDate fechaInicio, LocalDate fechaFin, List<String> dias,
                                    LocalTime horaInicio, LocalTime horaFin, Boolean usaParqueadero) {}

    public record RecurrenteDto(Long id, String nombre, String documento, String motivo,
                                Long inmuebleId, String inmueble, LocalDate fechaInicio,
                                LocalDate fechaFin, List<String> dias, LocalTime horaInicio,
                                LocalTime horaFin, boolean usaParqueadero, String estado) {}

    private final AutorizacionRepository autorizaciones;
    private final UsuarioRepository usuarios;
    private final VinculacionRepository vinculaciones;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    public AutorizacionRecurrenteController(AutorizacionRepository autorizaciones,
                                            UsuarioRepository usuarios,
                                            VinculacionRepository vinculaciones,
                                            AuditoriaService auditoria,
                                            NotificacionService notificaciones) {
        this.autorizaciones = autorizaciones;
        this.usuarios = usuarios;
        this.vinculaciones = vinculaciones;
        this.auditoria = auditoria;
        this.notificaciones = notificaciones;
    }

    // RF-038, RF-039 y RF-040
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RecurrenteDto registrar(@RequestBody RecurrenteRequest req, Authentication auth) {
        if (vacio(req.nombre()) || vacio(req.documento()) || vacio(req.motivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar nombre, documento y motivo del acceso");
        }
        if (req.fechaInicio() == null || req.fechaFin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar la fecha de inicio y la de fin de la vigencia");
        }
        if (req.fechaInicio().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La vigencia no puede empezar en el pasado");
        }
        if (req.fechaFin().isBefore(req.fechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
        if (req.horaInicio() == null || req.horaFin() == null
                || !req.horaInicio().isBefore(req.horaFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique una franja horaria válida: la hora de inicio debe ser menor que la de fin");
        }
        String dias = normalizarDias(req.dias());

        Integrante residente = integranteActual(auth);
        Inmueble inmueble = inmuebleParaRegistrar(residente, req.inmuebleId());

        Autorizacion a = new Autorizacion();
        a.setInmueble(inmueble);
        a.setAutorizante(residente);
        a.setTipo(TipoAutorizacion.RECURRENTE);
        a.setNombre(req.nombre().trim());
        a.setDocumento(req.documento().trim());
        a.setMotivo(req.motivo().trim());
        a.setFechaInicio(req.fechaInicio());
        a.setFechaFin(req.fechaFin());
        a.setDiasSemana(dias);
        a.setHoraInicio(req.horaInicio());
        a.setHoraFin(req.horaFin());
        // RN-10: no da derecho a parqueadero salvo indicación expresa.
        a.setUsaParqueaderoVisitantes(Boolean.TRUE.equals(req.usaParqueadero()));
        a = autorizaciones.save(a);

        // Sin nombre ni documento en la auditoría (minimización).
        auditoria.registrar("CREAR", "Autorizacion", a.getId(),
                "Autorización recurrente al inmueble " + inmueble.getIdentificacion());
        return aDto(a);
    }

    // RF-041: autorizaciones recurrentes vigentes de mis inmuebles (?todas=true incluye el resto).
    @GetMapping
    @Transactional(readOnly = true)
    public List<RecurrenteDto> consultar(@RequestParam(defaultValue = "false") boolean todas,
                                         Authentication auth) {
        Integrante residente = integranteActual(auth);
        List<RecurrenteDto> resultado = new ArrayList<>();
        for (Inmueble i : inmueblesDelResidente(residente)) {
            autorizaciones.findByInmuebleIdAndTipoOrderByIdDesc(i.getId(), TipoAutorizacion.RECURRENTE)
                    .stream()
                    .filter(a -> todas || a.estadoEfectivo() == EstadoAutorizacion.VIGENTE)
                    .map(this::aDto)
                    .forEach(resultado::add);
        }
        return resultado;
    }

    // RF-042: revocar antes del vencimiento.
    @PatchMapping("/{id}/revocar")
    @Transactional
    public RecurrenteDto revocar(@PathVariable Long id, Authentication auth) {
        Integrante residente = integranteActual(auth);
        Autorizacion a = autorizaciones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Autorización no encontrada"));

        boolean esDeMiInmueble = a.getTipo() == TipoAutorizacion.RECURRENTE
                && inmueblesDelResidente(residente).stream()
                        .anyMatch(i -> i.getId().equals(a.getInmueble().getId()));
        if (!esDeMiInmueble) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esa autorización no pertenece a su inmueble");
        }
        if (a.estadoEfectivo() != EstadoAutorizacion.VIGENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede revocar una autorización vigente");
        }
        a.setEstado(EstadoAutorizacion.REVOCADA);
        autorizaciones.save(a);
        auditoria.registrar("REVOCAR", "Autorizacion", a.getId(),
                "Autorización recurrente al inmueble " + a.getInmueble().getIdentificacion());

        // CU-17: aviso a los residentes del inmueble (RN-29). Un fallo no afecta a la revocación.
        notificaciones.avisar(EventoNotificacion.AUTORIZACION_RECURRENTE, a.getInmueble(),
                "Autorización recurrente revocada",
                "Se revocó la autorización de " + a.getNombre() + ".");
        return aDto(a);
    }

    private boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    // Acepta mayúsculas o minúsculas y tildes; guarda los días en orden de la semana.
    private String normalizarDias(List<String> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique al menos un día de la semana");
        }
        List<String> limpios = new ArrayList<>();
        for (String d : pedidos) {
            String n = d == null ? "" : d.trim().toUpperCase().replace("É", "E").replace("Á", "A");
            if (!DIAS.contains(n)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Día no válido: " + d + ". Use LUNES, MARTES, MIERCOLES, JUEVES, VIERNES, SABADO o DOMINGO");
            }
            limpios.add(n);
        }
        return String.join(",", DIAS.stream().filter(limpios::contains).toList());
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // RN-08: inmuebles donde es propietario residente o arrendatario con vinculación vigente.
    private List<Inmueble> inmueblesDelResidente(Integrante residente) {
        return vinculaciones
                .findByIntegranteIdAndEstado(residente.getId(), EstadoVinculacion.VIGENTE).stream()
                .filter(v -> v.getTipoVinculo() == TipoVinculo.PROPIETARIO_RESIDENTE
                          || v.getTipoVinculo() == TipoVinculo.ARRENDATARIO)
                .map(Vinculacion::getInmueble)
                .filter(Inmueble::isActivo)
                .toList();
    }

    private Inmueble inmuebleParaRegistrar(Integrante residente, Long inmuebleId) {
        List<Inmueble> propios = inmueblesDelResidente(residente);
        if (propios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Necesita una vinculación vigente como propietario residente o arrendatario");
        }
        if (inmuebleId != null) {
            return propios.stream().filter(i -> i.getId().equals(inmuebleId)).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "No tiene vinculación vigente con ese inmueble"));
        }
        if (propios.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Está vinculado a varios inmuebles: indique inmuebleId");
        }
        return propios.get(0);
    }

    private RecurrenteDto aDto(Autorizacion a) {
        List<String> dias = a.getDiasSemana() == null || a.getDiasSemana().isBlank()
                ? List.of() : Arrays.asList(a.getDiasSemana().split(","));
        return new RecurrenteDto(a.getId(), a.getNombre(), a.getDocumento(), a.getMotivo(),
                a.getInmueble().getId(), a.getInmueble().getIdentificacion(),
                a.getFechaInicio(), a.getFechaFin(), dias, a.getHoraInicio(), a.getHoraFin(),
                a.isUsaParqueaderoVisitantes(), a.estadoEfectivo().name());
    }
}