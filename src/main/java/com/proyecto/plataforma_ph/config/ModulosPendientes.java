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

    private static final Map<String, Modulo> POR_RUTA = Map.ofEntries(

        // ---------- Propietario, arrendatario y residente autorizado ----------
        e("/mis-inmuebles", "Mi inmueble", "RF-026", "Propietario, arrendatario y residente autorizado",
            "Consultar tus vinculaciones vigentes y la información permitida por tu vínculo.",
            "RF-026 Consultar tus propias vinculaciones vigentes",
            "RN-07 Solo ves la información de los inmuebles a los que estás vinculado")
    );
}