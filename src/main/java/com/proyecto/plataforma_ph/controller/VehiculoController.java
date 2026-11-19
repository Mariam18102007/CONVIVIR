package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.TipoVehiculo;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Vehiculo;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VehiculoRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-08: vehículos de los inmuebles.
 * Administrador: RF-045, 047, 048, 049, 050.
 * Propietario residente y arrendatario: RF-046, 047, 048 (solo su inmueble, RN-07).
 * Las rutas PUT y PATCH quedan solo para administrador en SecurityConfig.
 */
@RestController
@RequestMapping("/api/vehiculos")
public class VehiculoController {

    public record VehiculoDto(Long id, String placa, TipoVehiculo tipo, String marca, String color,
                              Long inmuebleId, String inmueble, Long responsableId, String responsable,
                              boolean activo, String motivoBaja) {}

    public record NuevoVehiculo(String placa, TipoVehiculo tipo, String marca, String color,
                                Long inmuebleId, Long responsableId) {}

    public record CambioVehiculo(String placa, TipoVehiculo tipo, String marca, String color,
                                 Long responsableId) {}

    public record Opcion(Long id, String nombre) {}

    // Quién puede ser responsable de un vehículo (RF-047).
    private static final Set<TipoVinculo> TIPOS_RESPONSABLE = Set.of(
            TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.PROPIETARIO_NO_RESIDENTE, TipoVinculo.ARRENDATARIO);

    // Quién puede registrar vehículos como residente (RF-046).
    private static final Set<TipoVinculo> TIPOS_RESIDENTE = Set.of(
            TipoVinculo.PROPIETARIO_RESIDENTE, TipoVinculo.ARRENDATARIO);

    private final VehiculoRepository vehiculos;
    private final InmuebleRepository inmuebles;
    private final VinculacionRepository vinculaciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public VehiculoController(VehiculoRepository vehiculos, InmuebleRepository inmuebles,
                              VinculacionRepository vinculaciones, UsuarioRepository usuarios,
                              AuditoriaService auditoria) {
        this.vehiculos = vehiculos;
        this.inmuebles = inmuebles;
        this.vinculaciones = vinculaciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // RF-048: administrador ve todos; el residente, solo los activos de su inmueble.
    @GetMapping
    @Transactional(readOnly = true)
    public List<VehiculoDto> listar(@RequestParam(required = false) Long inmuebleId,
                                    @RequestParam(required = false) Boolean activo,
                                    Authentication auth) {
        boolean admin = esAdmin(auth);
        Set<Long> propios = admin ? Set.of() : idsInmueblesResidente(auth);

        return vehiculos.findAll().stream()
                .filter(v -> admin || (v.isActivo() && propios.contains(v.getInmueble().getId())))
                .filter(v -> inmuebleId == null || v.getInmueble().getId().equals(inmuebleId))
                .filter(v -> activo == null || v.isActivo() == activo)
                .sorted(Comparator.comparing(Vehiculo::getPlaca))
                .map(this::dto)
                .toList();
    }

    // Inmuebles que puede elegir al registrar: todos los activos (administrador)
    // o los suyos (residente).
    @GetMapping("/inmuebles")
    @Transactional(readOnly = true)
    public List<Opcion> inmueblesDisponibles(Authentication auth) {
        if (esAdmin(auth)) {
            return inmuebles.findByActivo(true).stream()
                    .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                    .map(i -> new Opcion(i.getId(), i.getIdentificacion()))
                    .toList();
        }
        return inmueblesResidente(auth).stream()
                .filter(Inmueble::isActivo)
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .map(i -> new Opcion(i.getId(), i.getIdentificacion()))
                .toList();
    }

    // RF-047: propietarios y arrendatarios vinculados al inmueble.
    @GetMapping("/responsables")
    @Transactional(readOnly = true)
    public List<Opcion> responsables(@RequestParam Long inmuebleId, Authentication auth) {
        if (!esAdmin(auth) && !idsInmueblesResidente(auth).contains(inmuebleId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese inmueble no es tuyo");
        }
        return vinculaciones.findByInmuebleIdAndEstado(inmuebleId, EstadoVinculacion.VIGENTE).stream()
                .filter(v -> TIPOS_RESPONSABLE.contains(v.getTipoVinculo()))
                .map(v -> new Opcion(v.getIntegrante().getId(),
                        v.getIntegrante().getNombres() + " " + v.getIntegrante().getApellidos()
                                + " (" + etiquetaVinculo(v.getTipoVinculo()) + ")"))
                .toList();
    }

    // RF-045 (administrador) y RF-046 (residente).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public VehiculoDto crear(@RequestBody NuevoVehiculo body, Authentication auth) {
        String placa = normalizarPlaca(body.placa());
        validarDatos(body.tipo(), body.marca(), body.color());

        Inmueble inmueble = resolverInmueble(body.inmuebleId(), auth);
        if (!inmueble.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble está desactivado");
        }
        Integrante responsable = validarResponsable(body.responsableId(), inmueble.getId());

        if (vehiculos.existsByPlacaAndActivoTrue(placa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un vehículo activo con la placa " + placa);
        }

        Vehiculo v = new Vehiculo();
        v.setPlaca(placa);
        v.setTipo(body.tipo());
        v.setMarca(body.marca().trim());
        v.setColor(body.color().trim());
        v.setInmueble(inmueble);
        v.setResponsable(responsable);
        vehiculos.save(v);

        auditoria.registrar("CREAR", "Vehiculo", v.getId(), "Inmueble " + inmueble.getIdentificacion());
        return dto(v);
    }

    // RF-049: solo administrador.
    @PutMapping("/{id}")
    @Transactional
    public VehiculoDto modificar(@PathVariable Long id, @RequestBody CambioVehiculo body) {
        Vehiculo v = buscar(id);
        if (!v.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El vehículo está dado de baja");
        }
        String placa = normalizarPlaca(body.placa());
        validarDatos(body.tipo(), body.marca(), body.color());
        Integrante responsable = validarResponsable(body.responsableId(), v.getInmueble().getId());

        if (vehiculos.existsByPlacaAndActivoTrueAndIdNot(placa, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un vehículo activo con la placa " + placa);
        }

        v.setPlaca(placa);
        v.setTipo(body.tipo());
        v.setMarca(body.marca().trim());
        v.setColor(body.color().trim());
        v.setResponsable(responsable);
        vehiculos.save(v);

        auditoria.registrar("MODIFICAR", "Vehiculo", v.getId(), "Inmueble " + v.getInmueble().getIdentificacion());
        return dto(v);
    }

    // RF-050: solo administrador, con motivo obligatorio.
    @PatchMapping("/{id}/baja")
    @Transactional
    public VehiculoDto darDeBaja(@PathVariable Long id, @RequestParam String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }
        Vehiculo v = buscar(id);
        if (!v.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El vehículo ya está dado de baja");
        }
        v.setActivo(false);
        v.setMotivoBaja(motivo.trim());
        vehiculos.save(v);

        auditoria.registrar("DESACTIVAR", "Vehiculo", v.getId(), motivo.trim());
        return dto(v);
    }

    // ---------- Ayudas ----------

    private Vehiculo buscar(Long id) {
        return vehiculos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehículo no encontrado"));
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
    }

    private Long idIntegrante(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(u -> u.getIntegrante().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta no válida"));
    }

    // Inmuebles donde el residente tiene vinculación vigente como propietario residente o arrendatario.
    private List<Inmueble> inmueblesResidente(Authentication auth) {
        return vinculaciones.findByIntegranteIdAndEstado(idIntegrante(auth), EstadoVinculacion.VIGENTE).stream()
                .filter(v -> TIPOS_RESIDENTE.contains(v.getTipoVinculo()))
                .map(Vinculacion::getInmueble)
                .distinct()
                .toList();
    }

    private Set<Long> idsInmueblesResidente(Authentication auth) {
        return inmueblesResidente(auth).stream().map(Inmueble::getId).collect(java.util.stream.Collectors.toSet());
    }

    private Inmueble resolverInmueble(Long inmuebleId, Authentication auth) {
        if (esAdmin(auth)) {
            if (inmuebleId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el inmueble");
            }
            return inmuebles.findById(inmuebleId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        }
        List<Inmueble> propios = inmueblesResidente(auth);
        if (propios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes un inmueble vigente como propietario residente o arrendatario");
        }
        if (inmuebleId == null) {
            if (propios.size() > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el inmueble del vehículo");
            }
            return propios.get(0);
        }
        return propios.stream().filter(i -> i.getId().equals(inmuebleId)).findFirst().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese inmueble no es tuyo"));
    }

    private Integrante validarResponsable(Long responsableId, Long inmuebleId) {
        if (responsableId == null) return null;
        return vinculaciones.findByInmuebleIdAndEstado(inmuebleId, EstadoVinculacion.VIGENTE).stream()
                .filter(v -> v.getIntegrante().getId().equals(responsableId))
                .filter(v -> TIPOS_RESPONSABLE.contains(v.getTipoVinculo()))
                .map(Vinculacion::getIntegrante)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El responsable debe ser un propietario o arrendatario vinculado al inmueble"));
    }

    private String normalizarPlaca(String placa) {
        if (placa == null || placa.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La placa es obligatoria");
        }
        String p = placa.trim().toUpperCase().replaceAll("[\\s-]", "");
        if (!p.matches("[A-Z0-9]{5,7}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La placa debe tener entre 5 y 7 letras o números, por ejemplo ABC123");
        }
        return p;
    }

    private void validarDatos(TipoVehiculo tipo, String marca, String color) {
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El tipo de vehículo es obligatorio");
        }
        if (marca == null || marca.isBlank() || color == null || color.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La marca y el color son obligatorios");
        }
    }

    private String etiquetaVinculo(TipoVinculo t) {
        String s = t.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private VehiculoDto dto(Vehiculo v) {
        Integrante r = v.getResponsable();
        return new VehiculoDto(v.getId(), v.getPlaca(), v.getTipo(), v.getMarca(), v.getColor(),
                v.getInmueble().getId(), v.getInmueble().getIdentificacion(),
                r == null ? null : r.getId(),
                r == null ? null : r.getNombres() + " " + r.getApellidos(),
                v.isActivo(), v.getMotivoBaja());
    }
}