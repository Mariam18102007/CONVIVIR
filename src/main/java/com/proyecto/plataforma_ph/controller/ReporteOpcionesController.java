package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.*;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Inmuebles que cada rol puede elegir al crear un reporte.
 * Vigilante y administrador: todos los activos. Residentes: solo aquellos donde tienen
 * vinculación vigente como propietario residente o arrendatario (RN-07).
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteOpcionesController {

    public record Opcion(Long id, String nombre) {}

    private static final Set<TipoVinculo> TIPOS_RESIDENTE =
            Set.of(TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.ARRENDATARIO);

    private final InmuebleRepository inmuebles;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;

    public ReporteOpcionesController(InmuebleRepository inmuebles, VinculacionRepository vinculaciones,
                                     UsuarioRepository usuarios) {
        this.inmuebles = inmuebles;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
    }

    @GetMapping("/inmuebles")
    @Transactional(readOnly = true)
    public List<Opcion> inmueblesDisponibles(Authentication auth) {
        Integrante yo = usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));

        List<Inmueble> lista;
        if (yo.getRol() == RolIntegrante.VIGILANTE || yo.getRol() == RolIntegrante.ADMINISTRADOR) {
            lista = inmuebles.findByActivo(true);
        } else {
            lista = vinculaciones.findByIntegranteIdAndEstado(yo.getId(), EstadoVinculacion.VIGENTE).stream()
                    .filter(v -> TIPOS_RESIDENTE.contains(v.getTipoVinculo()))
                    .map(Vinculacion::getInmueble)
                    .filter(Inmueble::isActivo)
                    .distinct()
                    .toList();
        }
        return lista.stream()
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .map(i -> new Opcion(i.getId(), i.getIdentificacion()))
                .toList();
    }
}