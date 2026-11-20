package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.TokenRecuperacion;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.TokenRecuperacionRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import com.proyecto.plataforma_ph.service.CorreoService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// RF-002: recuperación de contraseña con token temporal de un solo uso.
@Controller
public class RecuperacionController {

    private static final int MINUTOS_VIGENCIA = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final TokenRecuperacionRepository tokens;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final CorreoService correos;
    private final String baseUrl;

    public RecuperacionController(UsuarioRepository usuarios, TokenRecuperacionRepository tokens,
                                  PasswordEncoder encoder, AuditoriaService auditoria,
                                  CorreoService correos,
                                  @Value("${app.base-url}") String baseUrl) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.correos = correos;
        // El enlace se arma con la URL configurada, nunca con el Host de la petición.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping("/recuperar")
    public String formularioCorreo() {
        return "recuperar";
    }

    // La respuesta es la misma exista o no la cuenta, para no revelar qué correos están registrados.
    @PostMapping("/recuperar")
    @Transactional
    public String solicitar(@RequestParam String correo, Model model) {
        Optional<Usuario> encontrado = usuarios.findByCorreoIgnoreCase(correo.trim())
                .filter(Usuario::isActivo);

        if (encontrado.isPresent()) {
            Usuario u = encontrado.get();

            // Invalida los enlaces anteriores sin usar.
            tokens.findByUsuarioIdAndUsadoFalse(u.getId()).forEach(t -> t.setUsado(true));

            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            TokenRecuperacion t = new TokenRecuperacion();
            t.setUsuario(u);
            t.setTokenHash(hash(token));
            t.setExpira(LocalDateTime.now().plusMinutes(MINUTOS_VIGENCIA));
            tokens.save(t);

            String enlace = baseUrl + "/restablecer?token=" + token;
            // Asíncrono: el tiempo de respuesta no revela si el correo existe.
            correos.enviar(u.getCorreo(), "Restablece tu contraseña · Convivir",
                    "Hola,\n\nRecibimos una solicitud para restablecer tu contraseña.\n"
                            + "Entra al siguiente enlace (vale " + MINUTOS_VIGENCIA
                            + " minutos y solo sirve una vez):\n\n"
                            + enlace + "\n\nSi no fuiste tú, ignora este mensaje.");

            auditoria.registrar("SOLICITAR_RECUPERACION", "Usuario", u.getId(),
                    "Cuenta: " + u.getCorreo());
        }

        model.addAttribute("enviado", true);
        return "recuperar";
    }

    @GetMapping("/restablecer")
    @Transactional(readOnly = true)
    public String formularioNueva(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("invalido", buscarValido(token).isEmpty());
        return "restablecer";
    }

    @PostMapping("/restablecer")
    @Transactional
    public String restablecer(@RequestParam String token, @RequestParam String password,
                              @RequestParam String confirmar, Model model) {
        model.addAttribute("token", token);
        Optional<TokenRecuperacion> t = buscarValido(token);
        if (t.isEmpty()) {
            model.addAttribute("invalido", true);
            return "restablecer";
        }
        if (password.length() < 8) {
            model.addAttribute("error", "La contraseña debe tener al menos 8 caracteres.");
            return "restablecer";
        }
        if (!password.equals(confirmar)) {
            model.addAttribute("error", "Las contraseñas no coinciden.");
            return "restablecer";
        }

        Usuario u = t.get().getUsuario();

        // Si la cuenta se desactivó después de pedir el enlace, no se restablece.
        if (!u.isActivo()) {
            model.addAttribute("invalido", true);
            return "restablecer";
        }

        u.setPasswordHash(encoder.encode(password));
        u.setBloqueado(false);
        u.setIntentosFallidos(0);
        usuarios.save(u);

        // El token queda gastado, y cualquier otro pendiente también.
        tokens.findByUsuarioIdAndUsadoFalse(u.getId()).forEach(x -> x.setUsado(true));

        auditoria.registrar("RECUPERAR_PASSWORD", "Usuario", u.getId(), "Cuenta: " + u.getCorreo());
        return "redirect:/login?restablecido";
    }

    private Optional<TokenRecuperacion> buscarValido(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(token))
                .filter(t -> !t.isUsado() && t.getExpira().isAfter(LocalDateTime.now()));
    }

    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}