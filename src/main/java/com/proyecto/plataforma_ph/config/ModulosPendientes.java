package com.proyecto.plataforma_ph.config;

import java.util.List;
import java.util.Map;

/**
 * Módulos que todavía no están construidos. Hoy no queda ninguno.
 * Si se agrega uno, se registra aquí y se mostrará la pantalla "Próximamente".
 */
public final class ModulosPendientes {

    public record Modulo(String titulo, String casoUso, String quien,
                         String descripcion, List<String> funciones) {}

    private ModulosPendientes() {}

    public static Modulo buscar(String ruta) {
        return POR_RUTA.get(ruta);
    }

    private static final Map<String, Modulo> POR_RUTA = Map.of();
}
