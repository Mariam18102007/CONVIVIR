package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.ConfiguracionCopropiedad;
import com.proyecto.plataforma_ph.repository.ConfiguracionCopropiedadRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** RF-106: nombre, NIT, dirección y datos de contacto de la propiedad horizontal. */
@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record DatosCopropiedad(String nombre, String nit, String direccion,
                                   String telefono, String correo) {}

    private final ConfiguracionCopropiedadRepository repo;
    private final AuditoriaService auditoria;

    public ConfiguracionController(ConfiguracionCopropiedadRepository repo, AuditoriaService auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DatosCopropiedad consultar() {
        return repo.findById(1L).map(this::dto)
                .orElse(new DatosCopropiedad(null, null, null, null, null));
    }

    @PutMapping
    @Transactional
    public DatosCopropiedad guardar(@RequestBody DatosCopropiedad body) {
        String nombre = texto(body.nombre(), 120, true, "El nombre es obligatorio");
        String correo = texto(body.correo(), 120, false, null);
        if (correo != null && !CORREO.matcher(correo).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo no es válido");
        }
        ConfiguracionCopropiedad c = repo.findById(1L).orElseGet(ConfiguracionCopropiedad::new);
        c.setNombre(nombre);
        c.setNit(texto(body.nit(), 30, false, null));
        c.setDireccion(texto(body.direccion(), 200, false, null));
        c.setTelefono(texto(body.telefono(), 30, false, null));
        c.setCorreo(correo);
        repo.save(c);

        auditoria.registrar("CONFIGURAR", "ConfiguracionCopropiedad", 1L, "Datos de la copropiedad");
        return dto(c);
    }

    private String texto(String s, int max, boolean obligatorio, String mensajeVacio) {
        if (s == null || s.isBlank()) {
            if (obligatorio) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeVacio);
            return null;
        }
        String t = s.trim();
        if (t.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El texto no puede pasar de " + max + " caracteres");
        }
        return t;
    }

    private DatosCopropiedad dto(ConfiguracionCopropiedad c) {
        return new DatosCopropiedad(c.getNombre(), c.getNit(), c.getDireccion(), c.getTelefono(), c.getCorreo());
    }
}