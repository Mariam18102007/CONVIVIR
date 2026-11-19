package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    public record CrearUsuario(Long integranteId, String correo, String password) {}

    public record RestablecerPassword(String nueva) {}

    public record UsuarioDto(Long id, String correo, String rol, Long integranteId,
                             boolean activo, boolean bloqueado) {}

    private final UsuarioRepository usuarios;
    private final IntegranteRepository integrantes;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public UsuarioController(UsuarioRepository usuarios, IntegranteRepository integrantes,
                             PasswordEncoder encoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.integrantes = integrantes;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    // RF-105: listar cuentas con su rol y estado
    @GetMapping
    @Transactional(readOnly = true)
    public List<UsuarioDto> listar() {
        return usuarios.findAll().stream().map(this::aDto).toList();
    }

    // RF-101: crear cuenta asociada a un integrante (el rol sale del integrante, RF-102)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioDto crear(@RequestBody CrearUsuario body) {
        if (body.integranteId() == null || body.correo() == null || body.correo().isBlank()
                || body.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar integranteId, correo y password");
        }
        if (body.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos 8 caracteres");
        }
        Integrante i = integrantes.findById(body.integranteId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Integrante no encontrado"));
        if (!i.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El integrante está desactivado");
        }
        if (usuarios.existsByIntegranteId(i.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El integrante ya tiene una cuenta");
        }
        if (usuarios.findByCorreoIgnoreCase(body.correo().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }

        Usuario u = new Usuario();
        u.setCorreo(body.correo().trim());
        u.setPasswordHash(encoder.encode(body.password()));
        u.setIntegrante(i);
        u = usuarios.save(u);

        auditoria.registrar("CREAR", "Usuario", u.getId(), "Correo: " + u.getCorreo());
        return aDto(u);
    }

    // RF-103: desactivar cuenta indicando el motivo
    @PatchMapping("/{id}/desactivar")
    @Transactional
    public UsuarioDto desactivar(@PathVariable Long id, @RequestParam String motivo,
                                 Authentication auth) {
        if (motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }
        Usuario u = buscar(id);
        if (u.getCorreo().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puede desactivar su propia cuenta");
        }
        u.setActivo(false);
        usuarios.save(u);
        auditoria.registrar("DESACTIVAR", "Usuario", u.getId(), "Motivo: " + motivo);
        return aDto(u);
    }

    @PatchMapping("/{id}/reactivar")
    @Transactional
    public UsuarioDto reactivar(@PathVariable Long id) {
        Usuario u = buscar(id);
        u.setActivo(true);
        usuarios.save(u);
        auditoria.registrar("REACTIVAR", "Usuario", u.getId(), null);
        return aDto(u);
    }

    // Quita el bloqueo por intentos fallidos
    @PatchMapping("/{id}/desbloquear")
    @Transactional
    public UsuarioDto desbloquear(@PathVariable Long id) {
        Usuario u = buscar(id);
        u.setBloqueado(false);
        u.setIntentosFallidos(0);
        usuarios.save(u);
        auditoria.registrar("DESBLOQUEAR", "Usuario", u.getId(), null);
        return aDto(u);
    }

    // RF-104: el administrador restablece la contraseña de una cuenta (y la desbloquea).
    @PatchMapping("/{id}/password")
    @Transactional
    public UsuarioDto restablecerPassword(@PathVariable Long id, @RequestBody RestablecerPassword body) {
        if (body.nueva() == null || body.nueva().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe tener al menos 8 caracteres");
        }
        Usuario u = buscar(id);
        u.setPasswordHash(encoder.encode(body.nueva()));
        u.setBloqueado(false);
        u.setIntentosFallidos(0);
        usuarios.save(u);
        auditoria.registrar("RESTABLECER_PASSWORD", "Usuario", u.getId(), null);
        return aDto(u);
    }

    private Usuario buscar(Long id) {
        return usuarios.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }

    private UsuarioDto aDto(Usuario u) {
        return new UsuarioDto(u.getId(), u.getCorreo(), u.getIntegrante().getRol().name(),
                u.getIntegrante().getId(), u.isActivo(), u.isBloqueado());
    }
}