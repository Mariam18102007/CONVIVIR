package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/integrantes")
public class IntegranteController {

    private final IntegranteRepository integrantes;
    private final AuditoriaService auditoria;

    public IntegranteController(IntegranteRepository integrantes, AuditoriaService auditoria) {
        this.integrantes = integrantes;
        this.auditoria = auditoria;
    }

    // RF-015 y RF-016 (búsqueda por documento o apellido)
    @GetMapping
    public List<Integrante> listar(@RequestParam(required = false) String documento,
                                   @RequestParam(required = false) String apellido) {
        if (documento != null) {
            return integrantes.findByNumeroDocumento(documento).map(List::of).orElse(List.of());
        }
        if (apellido != null) {
            return integrantes.findByApellidosContainingIgnoreCase(apellido);
        }
        return integrantes.findAll();
    }

    // RF-017
    @GetMapping("/{id}")
    public Integrante detalle(@PathVariable Long id) {
        return buscar(id);
    }

    // RF-014
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Integrante crear(@Valid @RequestBody Integrante body) {
        if (integrantes.existsByNumeroDocumento(body.getNumeroDocumento())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un integrante con ese documento");
        }
        body.setActivo(true);
        Integrante guardado = integrantes.save(body);
        auditoria.registrar("CREAR", "Integrante", guardado.getId(),
                "Rol: " + guardado.getRol() + ", documento: " + guardado.getNumeroDocumento());
        return guardado;
    }

    // RF-018: solo teléfono y correo
    @PutMapping("/{id}")
    @Transactional
    public Integrante modificarContacto(@PathVariable Long id, @RequestBody Integrante body) {
        Integrante i = buscar(id);
        i.setTelefono(body.getTelefono());
        i.setCorreo(body.getCorreo());
        Integrante guardado = integrantes.save(i);
        auditoria.registrar("MODIFICAR", "Integrante", guardado.getId(), "Datos de contacto");
        return guardado;
    }

    // RF-019 (RN-05: no elimina nada, solo desactiva)
    @PatchMapping("/{id}/desactivar")
    @Transactional
    public Integrante desactivar(@PathVariable Long id, @RequestParam String motivo) {
        if (motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }
        Integrante i = buscar(id);
        i.setActivo(false);
        i.setMotivoDesactivacion(motivo);
        Integrante guardado = integrantes.save(i);
        auditoria.registrar("DESACTIVAR", "Integrante", guardado.getId(), "Motivo: " + motivo);
        return guardado;
    }

    private Integrante buscar(Long id) {
        return integrantes.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Integrante no encontrado"));
    }
}
