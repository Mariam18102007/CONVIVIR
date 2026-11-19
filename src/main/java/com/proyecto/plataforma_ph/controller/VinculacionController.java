package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class VinculacionController {

    private static final Set<TipoVinculo> TIPOS_PROPIEDAD =
            Set.of(TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.PROPIETARIO_NO_RESIDENTE);

    public record VinculacionRequest(
            @NotNull Long integranteId,
            @NotNull Long inmuebleId,
            @NotNull TipoVinculo tipoVinculo,
            @NotNull LocalDate fechaInicio) {
    }

    private final VinculacionRepository vinculaciones;
    private final IntegranteRepository integrantes;
    private final InmuebleRepository inmuebles;
    private final AuditoriaService auditoria;

    public VinculacionController(VinculacionRepository vinculaciones,
                                 IntegranteRepository integrantes,
                                 InmuebleRepository inmuebles,
                                 AuditoriaService auditoria) {
        this.vinculaciones = vinculaciones;
        this.integrantes = integrantes;
        this.inmuebles = inmuebles;
        this.auditoria = auditoria;
    }

    // RF-020
    @PostMapping("/vinculaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Vinculacion vincular(@Valid @RequestBody VinculacionRequest req) {
        Integrante integrante = integrantes.findById(req.integranteId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Integrante no encontrado"));
        Inmueble inmueble = inmuebles.findById(req.inmuebleId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));

        if (!integrante.isActivo() || !inmueble.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El integrante y el inmueble deben estar activos");
        }
        // RN-03: un inmueble no puede tener más de una vinculación de propiedad vigente
        if (TIPOS_PROPIEDAD.contains(req.tipoVinculo())
                && !vinculaciones.findByInmuebleIdAndEstadoAndTipoVinculoIn(
                        inmueble.getId(), EstadoVinculacion.VIGENTE, TIPOS_PROPIEDAD).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El inmueble ya tiene un propietario vigente");
        }

        Vinculacion v = new Vinculacion();
        v.setIntegrante(integrante);
        v.setInmueble(inmueble);
        v.setTipoVinculo(req.tipoVinculo());
        v.setFechaInicio(req.fechaInicio());
        v.setEstado(EstadoVinculacion.VIGENTE);
        Vinculacion guardada = vinculaciones.save(v);
        auditoria.registrar("CREAR", "Vinculacion", guardada.getId(),
                "Integrante " + integrante.getId() + " -> inmueble " + inmueble.getId()
                        + " como " + req.tipoVinculo());
        return guardada;
    }

    // RF-021 (RN-04: no se elimina, queda como historial)
    @PatchMapping("/vinculaciones/{id}/finalizar")
    @Transactional
    public Vinculacion finalizar(@PathVariable Long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        Vinculacion v = buscar(id);
        if (v.getEstado() != EstadoVinculacion.VIGENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La vinculación ya está finalizada");
        }
        if (fechaFin.isBefore(v.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de fin no puede ser anterior a la de inicio");
        }
        v.setFechaFin(fechaFin);
        v.setEstado(EstadoVinculacion.FINALIZADA);
        Vinculacion guardada = vinculaciones.save(v);
        auditoria.registrar("FINALIZAR", "Vinculacion", guardada.getId(), "Fecha de fin: " + fechaFin);
        return guardada;
    }

    // RF-025
    @PatchMapping("/vinculaciones/{id}/tipo")
    @Transactional
    public Vinculacion corregirTipo(@PathVariable Long id,
                                    @RequestParam TipoVinculo tipoVinculo,
                                    @RequestParam String motivo) {
        if (motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }
        Vinculacion v = buscar(id);
        if (v.getEstado() != EstadoVinculacion.VIGENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se corrigen vinculaciones vigentes");
        }
        if (TIPOS_PROPIEDAD.contains(tipoVinculo)) {
            boolean otroPropietario = vinculaciones
                    .findByInmuebleIdAndEstadoAndTipoVinculoIn(
                            v.getInmueble().getId(), EstadoVinculacion.VIGENTE, TIPOS_PROPIEDAD)
                    .stream().anyMatch(o -> !o.getId().equals(v.getId()));
            if (otroPropietario) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El inmueble ya tiene un propietario vigente");
            }
        }
        TipoVinculo anterior = v.getTipoVinculo();
        v.setTipoVinculo(tipoVinculo);
        v.setMotivoCorreccion(motivo);
        Vinculacion guardada = vinculaciones.save(v);
        auditoria.registrar("MODIFICAR", "Vinculacion", guardada.getId(),
                "Tipo: " + anterior + " -> " + tipoVinculo + ". Motivo: " + motivo);
        return guardada;
    }

    // RF-022 y RF-023: ?estado=VIGENTE (por defecto) o ?estado=FINALIZADA
    @GetMapping("/inmuebles/{inmuebleId}/vinculaciones")
    public List<Vinculacion> porInmueble(@PathVariable Long inmuebleId,
                                         @RequestParam(defaultValue = "VIGENTE") EstadoVinculacion estado) {
        return vinculaciones.findByInmuebleIdAndEstado(inmuebleId, estado);
    }

    // RF-024
    @GetMapping("/integrantes/{integranteId}/vinculaciones")
    public List<Vinculacion> porIntegrante(@PathVariable Long integranteId) {
        return vinculaciones.findByIntegranteIdAndEstado(integranteId, EstadoVinculacion.VIGENTE);
    }

    private Vinculacion buscar(Long id) {
        return vinculaciones.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Vinculación no encontrada"));
    }
}