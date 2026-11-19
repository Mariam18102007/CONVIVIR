package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.AutorizacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// CU-05: visitas ocasionales registradas por propietarios residentes y arrendatarios.
@RestController
@RequestMapping("/api/visitas")
public class VisitaController {

    public record VisitaRequest(Long inmuebleId, String nombre, String documento,
                                LocalDate fechaInicio, LocalDate fechaFin, Boolean usaParqueadero) {}

    public record VisitaDto(Long id, String nombre, String documento, Long inmuebleId,
                            String inmueble, LocalDate fechaInicio, LocalDate fechaFin,
                            boolean usaParqueadero, String estado) {}

    private final AutorizacionRepository autorizaciones;
    private final UsuarioRepository usuarios;
    private final VinculacionRepository vinculaciones;
    private final AuditoriaService auditoria;

    public VisitaController(AutorizacionRepository autorizaciones, UsuarioRepository usuarios,
                            VinculacionRepository vinculaciones, AuditoriaService auditoria) {
        this.autorizaciones = autorizaciones;
        this.usuarios = usuarios;
        this.vinculaciones = vinculaciones;
        this.auditoria = auditoria;
    }

    // RF-027 y RF-028
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public VisitaDto registrar(@RequestBody VisitaRequest req, Authentication auth) {
        if (req.nombre() == null || req.nombre().isBlank()
                || req.documento() == null || req.documento().isBlank()
                || req.fechaInicio() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar nombre, documento y fecha de la visita");
        }
        LocalDate inicio = req.fechaInicio();
        LocalDate fin = req.fechaFin() != null ? req.fechaFin() : inicio;
        if (inicio.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de la visita no puede estar en el pasado");
        }
        if (fin.isBefore(inicio)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la inicial");
        }

        Integrante residente = integranteActual(auth);
        Inmueble inmueble = inmuebleDelResidente(residente, req.inmuebleId());

        Autorizacion a = new Autorizacion();
        a.setInmueble(inmueble);
        a.setAutorizante(residente);
        a.setTipo(TipoAutorizacion.OCASIONAL);
        a.setNombre(req.nombre().trim());
        a.setDocumento(req.documento().trim());
        a.setFechaInicio(inicio);
        a.setFechaFin(fin);
        a.setUsaParqueaderoVisitantes(Boolean.TRUE.equals(req.usaParqueadero()));
        a = autorizaciones.save(a);

        // No se guardan datos del visitante en la auditoría (minimización).
        auditoria.registrar("CREAR", "Autorizacion", a.getId(),
                "Visita ocasional al inmueble " + inmueble.getIdentificacion());
        return aDto(a);
    }

    // RF-029: visitas que el usuario registró, con su estado.
    @GetMapping
    @Transactional(readOnly = true)
    public List<VisitaDto> misVisitas(Authentication auth) {
        Integrante residente = integranteActual(auth);
        return autorizaciones
                .findByAutorizanteIdAndTipoOrderByIdDesc(residente.getId(), TipoAutorizacion.OCASIONAL)
                .stream().map(this::aDto).toList();
    }

    // RF-030: solo se cancela una visita vigente, es decir, que todavía no ha ingresado.
    @PatchMapping("/{id}/cancelar")
    @Transactional
    public VisitaDto cancelar(@PathVariable Long id, Authentication auth) {
        Integrante residente = integranteActual(auth);
        Autorizacion a = autorizaciones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!a.getAutorizante().getId().equals(residente.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esa visita no es suya");
        }
        if (a.getEstado() != EstadoAutorizacion.VIGENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede cancelar una visita que aún no ha ingresado");
        }
        a.setEstado(EstadoAutorizacion.CANCELADA);
        autorizaciones.save(a);
        auditoria.registrar("CANCELAR", "Autorizacion", a.getId(),
                "Visita ocasional al inmueble " + a.getInmueble().getIdentificacion());
        return aDto(a);
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // RN-08: solo con vinculación vigente de propietario residente o arrendatario.
    private Inmueble inmuebleDelResidente(Integrante residente, Long inmuebleId) {
        List<Inmueble> propios = vinculaciones
                .findByIntegranteIdAndEstado(residente.getId(), EstadoVinculacion.VIGENTE).stream()
                .filter(v -> v.getTipoVinculo() == TipoVinculo.PROPIETARIO_RESIDENTE
                          || v.getTipoVinculo() == TipoVinculo.ARRENDATARIO)
                .map(Vinculacion::getInmueble)
                .filter(Inmueble::isActivo)
                .toList();

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

    private VisitaDto aDto(Autorizacion a) {
        return new VisitaDto(a.getId(), a.getNombre(), a.getDocumento(),
                a.getInmueble().getId(), a.getInmueble().getIdentificacion(),
                a.getFechaInicio(), a.getFechaFin(), a.isUsaParqueaderoVisitantes(),
                a.estadoEfectivo().name());
    }
}