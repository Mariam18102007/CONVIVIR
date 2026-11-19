package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

    public record CambiarPassword(String actual, String nueva) {}

    public record Perfil(String nombres, String apellidos, String telefono, String correo) {}

    private final UsuarioRepository usuarios;
    private final IntegranteRepository integrantes;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public CuentaController(UsuarioRepository usuarios, IntegranteRepository integrantes,
                            PasswordEncoder encoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.integrantes = integrantes;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    // RF-005: consultar los datos de contacto propios.
    @GetMapping("/perfil")
    @Transactional(readOnly = true)
    public Perfil perfil(Authentication auth) {
        Integrante i = integranteActual(auth);
        return new Perfil(i.getNombres(), i.getApellidos(), i.getTelefono(), i.getCorreo());
    }

    // RF-005: modificar nombre, teléfono y correo de contacto propios.
    // El login (Usuario.correo) no cambia: el correo de contacto es un dato aparte.
    @PutMapping("/perfil")
    @Transactional
    public Perfil modificarPerfil(@RequestBody Perfil body, Authentication auth) {
        String nombres = limpio(body.nombres());
        String apellidos = limpio(body.apellidos());
        String telefono = limpio(body.telefono());
        String correo = limpio(body.correo());
        if (nombres == null || apellidos == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre y el apellido son obligatorios");
        }
        if (nombres.length() > 100 || apellidos.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es demasiado largo");
        }
        if (telefono != null && !telefono.matches("^[0-9+()\\- ]{7,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El teléfono no es válido");
        }
        if (correo != null && !correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo no es válido");
        }
        Integrante i = integranteActual(auth);
        i.setNombres(nombres);
        i.setApellidos(apellidos);
        i.setTelefono(telefono);
        i.setCorreo(correo);
        integrantes.save(i);
        auditoria.registrar("MODIFICAR", "Integrante", i.getId(), "Datos de contacto propios");
        return new Perfil(i.getNombres(), i.getApellidos(), i.getTelefono(), i.getCorreo());
    }

    private static String limpio(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Integrante integranteActual(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // RF-003: el usuario cambia su propia contraseña indicando la actual y la nueva.
    @PutMapping("/password")
    @Transactional
    public Map<String, String> cambiarPassword(@RequestBody CambiarPassword body, Authentication auth) {
        if (body.actual() == null || body.nueva() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar la contraseña actual y la nueva");
        }
        if (body.nueva().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe tener al menos 8 caracteres");
        }
        if (body.nueva().equals(body.actual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser distinta de la actual");
        }

        Usuario u = usuarios.findByCorreoIgnoreCase(auth.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        if (!encoder.matches(body.actual(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña actual no es correcta");
        }

        u.setPasswordHash(encoder.encode(body.nueva()));
        usuarios.save(u);
        auditoria.registrar("CAMBIAR_PASSWORD", "Usuario", u.getId(), null);
        return Map.of("mensaje", "Contraseña actualizada");
    }
}
