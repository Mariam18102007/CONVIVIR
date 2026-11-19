package com.proyecto.plataforma_ph.config;

import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.RolIntegrante;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;


@Configuration
public class DatosIniciales {

    @Bean
    CommandLineRunner crearAdministrador(UsuarioRepository usuarios,
                                         IntegranteRepository integrantes,
                                         PasswordEncoder encoder) {
        return args -> {
            if (usuarios.count() > 0) {
                return;
            }

            Integrante admin = integrantes.findByNumeroDocumento("1000000000")
                    .orElseGet(() -> {
                        Integrante i = new Integrante();
                        i.setRol(RolIntegrante.ADMINISTRADOR);
                        i.setTipoDocumento("CC");
                        i.setNumeroDocumento("1000000000");
                        i.setNombres("Administrador");
                        i.setApellidos("Inicial");
                        i.setCorreo("admin@ph.local");
                        i.setResidente(false);
                        return integrantes.save(i);
                    });

            Usuario u = new Usuario();
            u.setCorreo("admin@ph.local");
            u.setPasswordHash(encoder.encode("Admin123*"));
            u.setIntegrante(admin);
            usuarios.save(u);

            System.out.println(">>> Administrador inicial creado: admin@ph.local / Admin123*");
        };
    }
}