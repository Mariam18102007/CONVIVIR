package com.proyecto.plataforma_ph.security;

import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario u = usuarios.buscarConIntegrante(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        // RF-001
        boolean habilitado = u.isActivo() && u.getIntegrante().isActivo();

        return User.withUsername(u.getCorreo())
                .password(u.getPasswordHash())
                .roles(u.getIntegrante().getRol().name())
                .disabled(!habilitado)
                .accountLocked(u.isBloqueado())
                .build();
    }
}
