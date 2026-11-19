package com.proyecto.plataforma_ph.controller;

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
@RequestMapping("/api/tipos-inmueble")
public class TipoInmuebleController {

    private final TipoInmuebleRepository tipos;
    private final InmuebleRepository inmuebles;
    private final AuditoriaService auditoria;

    public TipoInmuebleController(TipoInmuebleRepository tipos,
                                  InmuebleRepository inmuebles,
                                  AuditoriaService auditoria) {
        this.tipos = tipos;
        this.inmuebles = inmuebles;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<TipoInmueble> listar() {
        return tipos.findAll();
    }

    // RF-112
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TipoInmueble crear(@Valid @RequestBody TipoInmueble body) {
        body.setNombre(body.getNombre().trim());
        validarNombreUnico(body.getNombre(), null);
        body.setActivo(true);
        TipoInmueble guardado = tipos.save(body);
        auditoria.registrar("CREAR", "TipoInmueble", guardado.getId(),
                "Nombre: " + guardado.getNombre());
        return guardado;
    }

    // RF-113
    @PutMapping("/{id}")
    @Transactional
    public TipoInmueble modificar(@PathVariable Long id, @Valid @RequestBody TipoInmueble body) {
        TipoInmueble t = buscar(id);
        String nombre = body.getNombre().trim();
        validarNombreUnico(nombre, id);
        t.setNombre(nombre);
        t.setDescripcion(body.getDescripcion());
        t.setPermiteCesion(body.isPermiteCesion());
        t.setPermiteReserva(body.isPermiteReserva());
        t.setGeneraCuota(body.isGeneraCuota());
        TipoInmueble guardado = tipos.save(t);
        auditoria.registrar("MODIFICAR", "TipoInmueble", guardado.getId(),
                "Nombre: " + guardado.getNombre());
        return guardado;
    }

    // RF-114
    @PatchMapping("/{id}/desactivar")
    @Transactional
    public TipoInmueble desactivar(@PathVariable Long id) {
        TipoInmueble t = buscar(id);
        if (inmuebles.countByTipoInmuebleIdAndActivoTrue(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El tipo tiene inmuebles activos dependientes");
        }
        t.setActivo(false);
        TipoInmueble guardado = tipos.save(t);
        auditoria.registrar("DESACTIVAR", "TipoInmueble", guardado.getId(),
                "Nombre: " + guardado.getNombre());
        return guardado;
    }

    @PatchMapping("/{id}/reactivar")
    @Transactional
    public TipoInmueble reactivar(@PathVariable Long id) {
        TipoInmueble t = buscar(id);
        t.setActivo(true);
        TipoInmueble guardado = tipos.save(t);
        auditoria.registrar("REACTIVAR", "TipoInmueble", guardado.getId(),
                "Nombre: " + guardado.getNombre());
        return guardado;
    }

    private TipoInmueble buscar(Long id) {
        return tipos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de inmueble no encontrado"));
    }

    // El nombre no puede repetirse (sin distinguir mayúsculas).
    private void validarNombreUnico(String nombre, Long idActual) {
        boolean repetido = tipos.findAll().stream()
                .filter(x -> idActual == null || !x.getId().equals(idActual))
                .anyMatch(x -> x.getNombre().trim().equalsIgnoreCase(nombre));
        if (repetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un tipo de inmueble llamado \"" + nombre + "\"");
        }
    }
}
