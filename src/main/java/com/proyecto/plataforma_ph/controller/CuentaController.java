package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cuenta")
public class CuentaController {

    public record CambiarPassword(String actual, String nueva) {}

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public CuentaController(UsuarioRepository usuarios, PasswordEncoder encoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.auditoria = auditoria;
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
