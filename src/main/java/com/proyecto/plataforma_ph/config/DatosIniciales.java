package com.proyecto.plataforma_ph.config;

import com.proyecto.plataforma_ph.model.CategoriaReporte;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.RolIntegrante;
import com.proyecto.plataforma_ph.model.TipoInmueble;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.CategoriaReporteRepository;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.TipoInmuebleRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Datos iniciales. Cada bloque es independiente y se puede ejecutar muchas veces:
 * solo agrega lo que falta, sin duplicar, modificar ni borrar lo que ya existe.
 */
@Configuration
public class DatosIniciales {

    // Nombre, descripción, permite cesión, permite reserva, genera cuota.
    private record TipoSemilla(String nombre, String descripcion, boolean permiteCesion,
                               boolean permiteReserva, boolean generaCuota) {}

    private record CategoriaSemilla(String nombre, String descripcion) {}

    private static final List<TipoSemilla> TIPOS = List.of(
            new TipoSemilla("Apartamento", "Unidad residencial privada", false, false, true),
            new TipoSemilla("Casa", "Vivienda independiente", false, false, true),
            new TipoSemilla("Local comercial", "Local de uso comercial", false, false, true),
            new TipoSemilla("Oficina", "Unidad de uso empresarial", false, false, true),
            new TipoSemilla("Parqueadero", "Espacio de estacionamiento", true, false, false),
            new TipoSemilla("Depósito", "Cuarto útil o depósito", false, false, false));

    private static final List<CategoriaSemilla> CATEGORIAS = List.of(
            new CategoriaSemilla("Mantenimiento", "Daños y arreglos en las instalaciones"),
            new CategoriaSemilla("Seguridad", "Novedades de seguridad y vigilancia"),
            new CategoriaSemilla("Aseo", "Limpieza de áreas comunes"),
            new CategoriaSemilla("Convivencia", "Situaciones entre vecinos"),
            new CategoriaSemilla("Zonas comunes", "Uso y estado de las zonas comunes"),
            new CategoriaSemilla("Servicios públicos", "Agua, luz, gas y otros servicios"),
            new CategoriaSemilla("Otros", "Cualquier otra novedad"));

    // "Depósito", "deposito " y "DEPOSITO" cuentan como el mismo nombre.
    private static String clave(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    // Contraseña aleatoria de 14 caracteres con mayúscula, minúscula, número y símbolo.
    private static String generarPassword() {
        String mayusculas = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String minusculas = "abcdefghijkmnopqrstuvwxyz";
        String numeros = "23456789";
        String simbolos = "*#$%&";
        String todo = mayusculas + minusculas + numeros;
        SecureRandom azar = new SecureRandom();

        List<Character> letras = new ArrayList<>();
        letras.add(mayusculas.charAt(azar.nextInt(mayusculas.length())));
        letras.add(minusculas.charAt(azar.nextInt(minusculas.length())));
        letras.add(numeros.charAt(azar.nextInt(numeros.length())));
        letras.add(simbolos.charAt(azar.nextInt(simbolos.length())));
        for (int i = 0; i < 10; i++) {
            letras.add(todo.charAt(azar.nextInt(todo.length())));
        }
        Collections.shuffle(letras, azar);

        StringBuilder sb = new StringBuilder();
        for (char c : letras) sb.append(c);
        return sb.toString();
    }

    @Bean
    @Order(1)
    CommandLineRunner crearAdministrador(UsuarioRepository usuarios,
                                         IntegranteRepository integrantes,
                                         PasswordEncoder encoder,
                                         @Value("${app.admin.correo:admin@ph.local}") String correo,
                                         @Value("${app.admin.password:}") String passwordConfigurada) {
        return args -> {
            if (usuarios.count() > 0) {
                return;
            }

            boolean generada = passwordConfigurada == null || passwordConfigurada.isBlank();
            String password = generada ? generarPassword() : passwordConfigurada;

            Integrante admin = integrantes.findByNumeroDocumento("1000000000")
                    .orElseGet(() -> {
                        Integrante i = new Integrante();
                        i.setRol(RolIntegrante.ADMINISTRADOR);
                        i.setTipoDocumento("CC");
                        i.setNumeroDocumento("1000000000");
                        i.setNombres("Administrador");
                        i.setApellidos("Inicial");
                        i.setCorreo(correo);
                        i.setResidente(false);
                        return integrantes.save(i);
                    });

            Usuario u = new Usuario();
            u.setCorreo(correo);
            u.setPasswordHash(encoder.encode(password));
            u.setIntegrante(admin);
            usuarios.save(u);

            if (generada) {
                System.out.println(">>> Administrador inicial creado: " + correo
                        + " / " + password + "   (anótela: no se vuelve a mostrar; cámbiela en Mi cuenta)");
            } else {
                System.out.println(">>> Administrador inicial creado: " + correo
                        + " (contraseña tomada de ADMIN_PASSWORD)");
            }
        };
    }

    @Bean
    @Order(2)
    CommandLineRunner crearTiposInmueble(TipoInmuebleRepository tipos) {
        return args -> {
            Set<String> existentes = new HashSet<>();
            tipos.findAll().forEach(t -> existentes.add(clave(t.getNombre())));

            int creados = 0;
            for (TipoSemilla s : TIPOS) {
                if (existentes.contains(clave(s.nombre()))) {
                    continue;
                }
                TipoInmueble t = new TipoInmueble();
                t.setNombre(s.nombre());
                t.setDescripcion(s.descripcion());
                t.setPermiteCesion(s.permiteCesion());
                t.setPermiteReserva(s.permiteReserva());
                t.setGeneraCuota(s.generaCuota());
                t.setActivo(true);
                tipos.save(t);
                creados++;
            }
            if (creados > 0) {
                System.out.println(">>> Tipos de inmueble agregados: " + creados);
            }
        };
    }

    @Bean
    @Order(3)
    CommandLineRunner crearCategoriasReporte(CategoriaReporteRepository categorias) {
        return args -> {
            Set<String> existentes = new HashSet<>();
            categorias.findAll().forEach(c -> existentes.add(clave(c.getNombre())));

            int creadas = 0;
            for (CategoriaSemilla s : CATEGORIAS) {
                if (existentes.contains(clave(s.nombre()))) {
                    continue;
                }
                CategoriaReporte c = new CategoriaReporte();
                c.setNombre(s.nombre());
                c.setDescripcion(s.descripcion());
                c.setActivo(true);
                categorias.save(c);
                creadas++;
            }
            if (creadas > 0) {
                System.out.println(">>> Categorías de reporte agregadas: " + creadas);
            }
        };
    }
}