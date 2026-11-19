package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Naturaleza;
import com.proyecto.plataforma_ph.model.TipoInmueble;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.TipoInmuebleRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/inmuebles")
public class InmuebleController {

    private final InmuebleRepository inmuebles;
    private final TipoInmuebleRepository tipos;
    private final AuditoriaService auditoria;

    public InmuebleController(InmuebleRepository inmuebles,
                              TipoInmuebleRepository tipos,
                              AuditoriaService auditoria) {
        this.inmuebles = inmuebles;
        this.tipos = tipos;
        this.auditoria = auditoria;
    }

    // RF-007 y RF-008 
    @GetMapping
    public List<Inmueble> listar(@RequestParam(required = false) Long tipoId,
                                 @RequestParam(required = false) Naturaleza naturaleza,
                                 @RequestParam(required = false) Boolean activo,
                                 @RequestParam(required = false) Long contenedorId) {
        List<Inmueble> lista = inmuebles.findAll();
        return lista.stream()
                .filter(i -> tipoId == null || i.getTipoInmueble().getId().equals(tipoId))
                .filter(i -> naturaleza == null || i.getNaturaleza() == naturaleza)
                .filter(i -> activo == null || i.isActivo() == activo)
                .filter(i -> contenedorId == null
                        || (i.getContenedor() != null && i.getContenedor().getId().equals(contenedorId)))
                .toList();
    }

    // RF-009
    @GetMapping("/{id}")
    public Inmueble detalle(@PathVariable Long id) {
        return buscar(id);
    }

    // RF-006
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Inmueble crear(@Valid @RequestBody Inmueble body) {
        body.setIdentificacion(body.getIdentificacion().trim());
        validarIdentificacionUnica(body.getIdentificacion(), null);
        body.setTipoInmueble(resolverTipo(body.getTipoInmueble()));
        body.setContenedor(resolverContenedor(body.getContenedor()));
        body.setActivo(true);
        Inmueble guardado = inmuebles.save(body);
        auditoria.registrar("CREAR", "Inmueble", guardado.getId(),
                "Identificación: " + guardado.getIdentificacion());
        return guardado;
    }

    // RF-010
    @PutMapping("/{id}")
    @Transactional
    public Inmueble modificar(@PathVariable Long id, @Valid @RequestBody Inmueble body) {
        Inmueble i = buscar(id);
        String identificacion = body.getIdentificacion().trim();
        validarIdentificacionUnica(identificacion, id);
        i.setIdentificacion(identificacion);
        i.setCoeficiente(body.getCoeficiente());
        i.setTipoInmueble(resolverTipo(body.getTipoInmueble()));
        Inmueble guardado = inmuebles.save(i);
        auditoria.registrar("MODIFICAR", "Inmueble", guardado.getId(),
                "Identificación: " + guardado.getIdentificacion());
        return guardado;
    }

    // RF-011
    @PatchMapping("/{id}/desactivar")
    @Transactional
    public Inmueble desactivar(@PathVariable Long id, @RequestParam String motivo) {
        if (motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }
        Inmueble i = buscar(id);
        i.setActivo(false);
        i.setMotivoDesactivacion(motivo);
        Inmueble guardado = inmuebles.save(i);
        auditoria.registrar("DESACTIVAR", "Inmueble", guardado.getId(), "Motivo: " + motivo);
        return guardado;
    }

    // RF-012
    @PatchMapping("/{id}/reactivar")
    @Transactional
    public Inmueble reactivar(@PathVariable Long id) {
        Inmueble i = buscar(id);
        i.setActivo(true);
        i.setMotivoDesactivacion(null);
        Inmueble guardado = inmuebles.save(i);
        auditoria.registrar("REACTIVAR", "Inmueble", guardado.getId(), null);
        return guardado;
    }

    private Inmueble buscar(Long id) {
        return inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
    }

    // La identificación no puede repetirse (sin distinguir mayúsculas). idActual se
    // excluye al modificar, para que un inmueble no choque consigo mismo.
    private void validarIdentificacionUnica(String identificacion, Long idActual) {
        boolean repetida = inmuebles.findAll().stream()
                .filter(x -> idActual == null || !x.getId().equals(idActual))
                .anyMatch(x -> x.getIdentificacion().trim().equalsIgnoreCase(identificacion));
        if (repetida) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un inmueble con la identificación \"" + identificacion + "\"");
        }
    }

    private TipoInmueble resolverTipo(TipoInmueble t) {
        if (t == null || t.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe indicar el tipo de inmueble");
        }
        return tipos.findById(t.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de inmueble no encontrado"));
    }

    private Inmueble resolverContenedor(Inmueble c) {
        if (c == null || c.getId() == null) return null;
        return inmuebles.findById(c.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble contenedor no encontrado"));
    }
}