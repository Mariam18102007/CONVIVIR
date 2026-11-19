package com.proyecto.plataforma_ph.config;

import java.util.List;
import java.util.Map;

/**
 * Módulos que todavía no están construidos. Cada uno muestra una pantalla
 * "Próximamente" con lo que cubrirá, según el documento de requisitos.
 * Cuando un módulo se construye, se borra de aquí y se agrega su ruta en InicioController.
 */
public final class ModulosPendientes {

    public record Modulo(String titulo, String casoUso, String quien,
                         String descripcion, List<String> funciones) {}

    private ModulosPendientes() {}

    public static Modulo buscar(String ruta) {
        return POR_RUTA.get(ruta);
    }

    private static Map.Entry<String, Modulo> e(String ruta, String titulo, String casoUso,
                                                String quien, String descripcion, String... funciones) {
        return Map.entry(ruta, new Modulo(titulo, casoUso, quien, descripcion, List.of(funciones)));
    }

    // Hoy no queda ningún módulo pendiente.
    private static final Map<String, Modulo> POR_RUTA = Map.of();
}
