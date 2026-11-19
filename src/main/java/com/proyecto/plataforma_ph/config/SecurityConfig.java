package com.proyecto.plataforma_ph.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // RNF-01: las contraseñas se guardan con hash y salt (BCrypt).
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // La API REST sigue funcionando sin token CSRF (por ahora).
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            // Spring usa la primera regla que coincide: los módulos nuevos
            // agregan la suya ANTES de /api/**.
            .authorizeHttpRequests(auth -> auth
                // RF-002: rutas públicas (login, recuperación de contraseña y estilos).
                .requestMatchers("/login", "/recuperar", "/restablecer", "/css/**").permitAll()
                // RF-003: cualquier usuario con sesión gestiona su propia cuenta.
                .requestMatchers("/api/cuenta/**").authenticated()
                // CU-05 y CU-06: visitas y autorizaciones de los residentes.
                .requestMatchers("/api/visitas/**", "/api/autorizaciones/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                // CU-08: vehículos. Consultar y registrar: administrador y residentes
                // (el controlador limita al residente a su inmueble).
                // Modificar y dar de baja caen en /api/** (solo administrador).
                .requestMatchers(HttpMethod.GET, "/api/vehiculos/**")
                    .hasAnyRole("ADMINISTRADOR", "PROPIETARIO", "ARRENDATARIO")
                .requestMatchers(HttpMethod.POST, "/api/vehiculos")
                    .hasAnyRole("ADMINISTRADOR", "PROPIETARIO", "ARRENDATARIO")
                // CU-11 y CU-12: obligaciones. Consulta propia de propietarios y arrendatarios
                // (el controlador limita el alcance a sus inmuebles). El resto de
                // /api/obligaciones (periodos, generar, crear, listar, anular) cae en /api/**.
                .requestMatchers(HttpMethod.GET, "/api/obligaciones/mias", "/api/obligaciones/mias/resumen")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                // CU-12 y CU-13: pagos. Los residentes pagan por la pasarela, verifican el resultado
                // y consultan su historial (el controlador limita el alcance y que el arrendatario
                // pague solo expensas ordinarias).
                .requestMatchers(HttpMethod.GET, "/api/pagos/mios")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                .requestMatchers("/api/pagos/pasarela", "/api/pagos/pasarela/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                .requestMatchers(HttpMethod.POST, "/api/pagos/*/verificar")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                // Comprobante o recibo: el administrador y quien pagó (el controlador valida el acceso).
                .requestMatchers(HttpMethod.GET, "/api/pagos/*/comprobante")
                    .hasAnyRole("ADMINISTRADOR", "PROPIETARIO", "ARRENDATARIO")
                // El resto de /api/pagos (listar, pago presencial y anular) cae en /api/** (solo administrador).
                // CU-14: cartera e información financiera. Administrador y consejo solo consultan
                // (RN-31): listado y detalle de cartera, recaudo y gastos. Exportar, paz y salvo,
                // intereses de mora y registrar o anular gastos caen en /api/** (solo administrador).
                .requestMatchers(HttpMethod.GET, "/api/cartera", "/api/cartera/inmuebles/*")
                    .hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")
                .requestMatchers(HttpMethod.GET, "/api/recaudo")
                    .hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")
                .requestMatchers(HttpMethod.GET, "/api/gastos")
                    .hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")
                // CU-20: reservas de zonas comunes. Los residentes consultan zonas y disponibilidad,
                // solicitan, ven y cancelan las suyas (el controlador exige vínculo residente vigente
                // y que la reserva sea propia). Listar, confirmar, rechazar, finalizar, multar y
                // configurar zonas (CU-21) caen en /api/** (solo administrador).
                .requestMatchers(HttpMethod.GET, "/api/reservas/zonas", "/api/reservas/mis-inmuebles",
                        "/api/reservas/disponibilidad", "/api/reservas/mias")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "RESIDENTE_AUTORIZADO")
                .requestMatchers(HttpMethod.POST, "/api/reservas")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "RESIDENTE_AUTORIZADO")
                .requestMatchers(HttpMethod.PATCH, "/api/reservas/*/cancelar")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "RESIDENTE_AUTORIZADO")
                // CU-15 y CU-16: reportes y novedades.
                // Crear y adjuntar evidencia: quienes reportan (el controlador limita el alcance).
                .requestMatchers(HttpMethod.POST, "/api/reportes", "/api/reportes/*/evidencia")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "VIGILANTE")
                // Mis reportes y los de mi inmueble.
                .requestMatchers(HttpMethod.GET, "/api/reportes/mis-reportes", "/api/reportes/de-mi-inmueble")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "VIGILANTE")
                // Categorías (solo consulta): todos los que reportan y el administrador.
                .requestMatchers(HttpMethod.GET, "/api/reportes/categorias")
                    .hasAnyRole("ADMINISTRADOR", "PROPIETARIO", "ARRENDATARIO", "VIGILANTE")
                // Detalle e imagen de un reporte (el controlador valida que pueda verlo).
                .requestMatchers(HttpMethod.GET, "/api/reportes/*", "/api/reportes/*/evidencia")
                    .hasAnyRole("ADMINISTRADOR", "PROPIETARIO", "ARRENDATARIO", "VIGILANTE")
                // El resto de /api/reportes (listar, asignar, estado, seguimiento, cerrar y
                // gestión de categorías) cae en /api/** (solo administrador).
                // CU-07: portería, solo vigilantes.
                .requestMatchers("/api/porteria/**").hasRole("VIGILANTE")
                // El vigilante necesita ver el listado de inmuebles (solo consulta)
                // para registrar un ingreso sin autorización.
                .requestMatchers(HttpMethod.GET, "/api/inmuebles")
                    .hasAnyRole("ADMINISTRADOR", "VIGILANTE")
                // RF-108 y RF-109: administrador y consejo (RN-31).
                .requestMatchers("/api/auditoria/**").hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")

                // ---------- Páginas del administrador ----------
                .requestMatchers("/inmuebles/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/tipos-inmueble/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/integrantes/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/cuentas/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/vinculaciones/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/vehiculos/**", "/parqueaderos/**", "/historial-accesos/**",
                        "/obligaciones/**", "/pagos/**", "/reservas/**", "/reportes/**",
                        "/notificaciones/**", "/configuracion/**").hasRole("ADMINISTRADOR")

                // ---------- Administrador y consejo ----------
                .requestMatchers("/auditoria/**").hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")
                .requestMatchers("/cartera/**").hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")
                .requestMatchers("/gastos/**").hasAnyRole("ADMINISTRADOR", "MIEMBRO_CONSEJO")

                // ---------- Solo consejo ----------
                .requestMatchers("/recaudo/**").hasRole("MIEMBRO_CONSEJO")

                // ---------- Vigilante ----------
                .requestMatchers("/porteria/**", "/control-vehicular/**").hasRole("VIGILANTE")

                // ---------- Propietario, arrendatario y residente autorizado ----------
                .requestMatchers("/mis-inmuebles/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "RESIDENTE_AUTORIZADO")
                .requestMatchers("/zonas-comunes/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "RESIDENTE_AUTORIZADO")
                .requestMatchers("/visitas/**", "/autorizaciones/**", "/mis-vehiculos/**",
                        "/mis-obligaciones/**", "/pasarela-simulada/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO")
                .requestMatchers("/mis-reportes/**")
                    .hasAnyRole("PROPIETARIO", "ARRENDATARIO", "VIGILANTE")

                // Inmuebles, tipos, integrantes, vinculaciones, cuentas, historial de
                // accesos, gestión de obligaciones, pagos, cartera (exportar, paz y salvo,
                // intereses), gastos y gestión de reservas: solo administrador.
                .requestMatchers("/api/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("correo")
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error"))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            // Permite probar la API con usuario y contraseña desde PowerShell.
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}