package com.proyecto.plataforma_ph.security;

import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IntentosLoginListener {

    private final UsuarioRepository usuarios;
    private final int maxIntentos;

    public IntentosLoginListener(UsuarioRepository usuarios,
                                 @Value("${app.seguridad.max-intentos}") int maxIntentos) {
        this.usuarios = usuarios;
        this.maxIntentos = maxIntentos;
    }

    // Contraseña incorrecta: suma un intento y bloquea al llegar al máximo.
    @EventListener
    @Transactional
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        usuarios.findByCorreoIgnoreCase(evento.getAuthentication().getName()).ifPresent(u -> {
            u.setIntentosFallidos(u.getIntentosFallidos() + 1);
            if (u.getIntentosFallidos() >= maxIntentos) {
                u.setBloqueado(true);
            }
            usuarios.save(u);
        });
    }

    // Ingreso correcto: reinicia el contador.
    @EventListener
    @Transactional
    public void alEntrar(AuthenticationSuccessEvent evento) {
        usuarios.findByCorreoIgnoreCase(evento.getAuthentication().getName()).ifPresent(u -> {
            if (u.getIntentosFallidos() != 0) {
                u.setIntentosFallidos(0);
                usuarios.save(u);
            }
        });
    }
}
