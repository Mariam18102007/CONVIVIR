package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.CategoriaReporte;
import com.proyecto.plataforma_ph.repository.CategoriaReporteRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Categorías de reporte. Todos los roles que crean reportes pueden consultarlas (solo las activas);
 * crear, modificar y desactivar caen en /api/** (solo administrador).
 */
@RestController
@RequestMapping("/api/reportes/categorias")
public class CategoriaReporteController {

    public record NuevaCategoria(String nombre, String descripcion) {}

    public record CategoriaDto(Long id, String nombre, String descripcion, boolean activo) {}

    private final CategoriaReporteRepository categorias;
    private final AuditoriaService auditoria;

    public CategoriaReporteController(CategoriaReporteRepository categorias, AuditoriaService auditoria) {
        this.categorias = categorias;
        this.auditoria = auditoria;
    }

    // El administrador ve todas; los demás, solo las activas.
    @GetMapping
    @Transactional(readOnly = true)
    public List<CategoriaDto> listar(Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
        return categorias.findAll().stream()
                .filter(c -> admin || c.isActivo())
                .sorted(Comparator.comparing(c -> c.getNombre().toLowerCase()))
                .map(this::dto)
                .toList();
    }

    // RF-100
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CategoriaDto crear(@RequestBody NuevaCategoria body) {
        String nombre = nombreValido(body.nombre());
        validarNombreLibre(nombre, null);

        CategoriaReporte c = new CategoriaReporte();
        c.setNombre(nombre);
        c.setDescripcion(descripcion(body.descripcion()));
        categorias.save(c);

        auditoria.registrar("CREAR", "CategoriaReporte", c.getId(), nombre);
        return dto(c);
    }

    // RF-110
    @PutMapping("/{id}")
    @Transactional
    public CategoriaDto modificar(@PathVariable Long id, @RequestBody NuevaCategoria body) {
        CategoriaReporte c = buscar(id);
        if (!c.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría está desactivada");
        }
        String nombre = nombreValido(body.nombre());
        validarNombreLibre(nombre, id);

        c.setNombre(nombre);
        c.setDescripcion(descripcion(body.descripcion()));
        categorias.save(c);

        auditoria.registrar("MODIFICAR", "CategoriaReporte", c.getId(), nombre);
        return dto(c);
    }

    // RF-111: los reportes que ya la usan conservan su categoría.
    @PatchMapping("/{id}/desactivar")
    @Transactional
    public CategoriaDto desactivar(@PathVariable Long id) {
        CategoriaReporte c = buscar(id);
        if (!c.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría ya está desactivada");
        }
        c.setActivo(false);
        categorias.save(c);

        auditoria.registrar("DESACTIVAR", "CategoriaReporte", c.getId(), c.getNombre());
        return dto(c);
    }

    private CategoriaReporte buscar(Long id) {
        return categorias.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
    }

    private String nombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio");
        }
        String n = nombre.trim();
        if (n.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre no puede pasar de 60 caracteres");
        }
        return n;
    }

    private String descripcion(String d) {
        if (d == null || d.isBlank()) return null;
        String t = d.trim();
        if (t.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La descripción no puede pasar de 200 caracteres");
        }
        return t;
    }

    private void validarNombreLibre(String nombre, Long idPropio) {
        boolean repetido = categorias.findAll().stream().anyMatch(c ->
                c.getNombre().equalsIgnoreCase(nombre) && (idPropio == null || !c.getId().equals(idPropio)));
        if (repetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
        }
    }

    private CategoriaDto dto(CategoriaReporte c) {
        return new CategoriaDto(c.getId(), c.getNombre(), c.getDescripcion(), c.isActivo());
    }
}