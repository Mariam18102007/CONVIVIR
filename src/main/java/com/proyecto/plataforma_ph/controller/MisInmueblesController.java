package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-026: el propietario, arrendatario o residente autorizado consulta sus propias
 * vinculaciones vigentes. RN-07: solo ve los inmuebles a los que está vinculado.
 */
@RestController
@RequestMapping("/api/mis-inmuebles")
public class MisInmueblesController {

    public record MiInmueble(Long vinculacionId, Long inmuebleId, String identificacion,
                             String naturaleza, String tipoInmueble, String tipoVinculo,
                             LocalDate fechaInicio) {}

    private final UsuarioRepository usuarios;
    private final VinculacionRepository vinculaciones;

    public MisInmueblesController(UsuarioRepository usuarios, VinculacionRepository vinculaciones) {
        this.usuarios = usuarios;
        this.vinculaciones = vinculaciones;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<MiInmueble> mias(Authentication auth) {
        Integrante yo = usuarios.buscarConIntegrante(auth.getName())
                .map(Usuario::getIntegrante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));

        return vinculaciones.findByIntegranteIdAndEstado(yo.getId(), EstadoVinculacion.VIGENTE).stream()
                .sorted(Comparator.comparing((Vinculacion v) -> v.getInmueble().getIdentificacion()))
                .map(v -> new MiInmueble(
                        v.getId(),
                        v.getInmueble().getId(),
                        v.getInmueble().getIdentificacion(),
                        v.getInmueble().getNaturaleza().name(),
                        v.getInmueble().getTipoInmueble().getNombre(),
                        v.getTipoVinculo().name(),
                        v.getFechaInicio()))
                .toList();
    }
}
