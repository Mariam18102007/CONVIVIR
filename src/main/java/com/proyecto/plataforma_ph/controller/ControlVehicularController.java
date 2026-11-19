package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.MovimientoVehicular;
import com.proyecto.plataforma_ph.model.TipoVehiculo;
import com.proyecto.plataforma_ph.model.Vehiculo;
import com.proyecto.plataforma_ph.repository.InmuebleRepository;
import com.proyecto.plataforma_ph.repository.MovimientoVehicularRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VehiculoRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-10: control vehicular.
 * Vigilante (/api/porteria/**): RF-051, RF-052, RF-053, RF-054, RF-064.
 * Residentes (/api/visitas/**): RF-064.
 * Administrador (/api/accesos/**): RF-055.
 * El vigilante no modifica vehículos ni parqueaderos (RN-13).
 */
@RestController
public class ControlVehicularController {

    public record VehiculoConsulta(Long id, String placa, TipoVehiculo tipo, String marca, String color,
                                   Long inmuebleId, String inmueble, boolean dentro) {}

    public record IngresoVehiculo(String placa, Long inmuebleId, Long parqueaderoId) {}

    public record MovimientoDto(Long id, String placa, boolean visitante, String vehiculo,
                                Long inmuebleId, String inmueble, Long parqueaderoId, String parqueadero,
                                LocalDateTime ingresoEn, LocalDateTime salidaEn,
                                String vigilanteIngreso, String vigilanteSalida, boolean dentro) {}

    public record ParqueaderoVisitante(Long id, String identificacion, boolean ocupado) {}

    public record DisponibilidadVisitantes(int total, int ocupados, int disponibles,
                                           List<ParqueaderoVisitante> parqueaderos) {}

    private final VehiculoRepository vehiculos;
    private final InmuebleRepository inmuebles;
    private final MovimientoVehicularRepository movimientos;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public ControlVehicularController(VehiculoRepository vehiculos, InmuebleRepository inmuebles,
                                      MovimientoVehicularRepository movimientos, UsuarioRepository usuarios,
                                      AuditoriaService auditoria) {
        this.vehiculos = vehiculos;
        this.inmuebles = inmuebles;
        this.movimientos = movimientos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // RF-051: consultar un vehículo por su placa y ver su inmueble.
    @GetMapping("/api/porteria/vehiculos")
    @Transactional(readOnly = true)
    public VehiculoConsulta consultar(@RequestParam String placa) {
        String p = normalizarPlaca(placa);
        Vehiculo v = buscarActivoPorPlaca(p);
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay un vehículo registrado con esa placa");
        }
        return new VehiculoConsulta(v.getId(), v.getPlaca(), v.getTipo(), v.getMarca(), v.getColor(),
                v.getInmueble().getId(), v.getInmueble().getIdentificacion(),
                movimientos.existsByPlacaAndSalidaEnIsNull(v.getPlaca()));
    }

    // RF-052: registrar el ingreso de un vehículo (registrado o de visitante).
    @PostMapping("/api/porteria/vehiculos/ingresos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public MovimientoDto ingresar(@RequestBody IngresoVehiculo body, Authentication auth) {
        String placa = normalizarPlaca(body.placa());
        if (movimientos.existsByPlacaAndSalidaEnIsNull(placa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese vehículo ya está dentro del conjunto");
        }

        Vehiculo vehiculo = buscarActivoPorPlaca(placa);
        boolean visitante = vehiculo == null;

        Inmueble inmueble;
        if (visitante) {
            if (body.inmuebleId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El vehículo no está registrado: indica el inmueble que visita");
            }
            inmueble = inmuebles.findById(body.inmuebleId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Inmueble no encontrado"));
        } else {
            inmueble = vehiculo.getInmueble();
        }
        if (!inmueble.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El inmueble está desactivado");
        }

        Inmueble parqueadero = null;
        if (body.parqueaderoId() != null) {
            parqueadero = validarParqueadero(body.parqueaderoId(), visitante);
        }

        MovimientoVehicular m = new MovimientoVehicular();
        m.setVehiculo(vehiculo);
        m.setPlaca(placa);
        m.setInmueble(inmueble);
        m.setParqueadero(parqueadero);
        m.setIngresoEn(LocalDateTime.now());
        m.setVigilanteIngreso(nombreVigilante(auth));
        movimientos.save(m);

        auditoria.registrar("INGRESAR_VEHICULO", "MovimientoVehicular", m.getId(),
                "Inmueble " + inmueble.getIdentificacion());
        return dto(m);
    }

    // RF-053: registrar la salida de un vehículo que está dentro.
    @PatchMapping("/api/porteria/vehiculos/ingresos/{id}/salida")
    @Transactional
    public MovimientoDto salida(@PathVariable Long id, Authentication auth) {
        MovimientoVehicular m = movimientos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        if (m.getSalidaEn() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La salida de ese vehículo ya fue registrada");
        }
        m.setSalidaEn(LocalDateTime.now());
        m.setVigilanteSalida(nombreVigilante(auth));
        movimientos.save(m);

        auditoria.registrar("SALIR_VEHICULO", "MovimientoVehicular", m.getId(),
                "Inmueble " + m.getInmueble().getIdentificacion());
        return dto(m);
    }

    // RF-054: vehículos que están dentro del conjunto.
    @GetMapping("/api/porteria/vehiculos/dentro")
    @Transactional(readOnly = true)
    public List<MovimientoDto> dentro() {
        return movimientos.findBySalidaEnIsNullOrderByIngresoEnDesc().stream().map(this::dto).toList();
    }

    // RF-064: parqueaderos de visitantes disponibles (vigilante y residentes).
    @GetMapping({"/api/porteria/parqueaderos-visitantes", "/api/visitas/parqueaderos-disponibles"})
    @Transactional(readOnly = true)
    public DisponibilidadVisitantes disponibilidad() {
        Set<Long> ocupados = movimientos.findBySalidaEnIsNullOrderByIngresoEnDesc().stream()
                .filter(m -> m.getParqueadero() != null)
                .map(m -> m.getParqueadero().getId())
                .collect(Collectors.toSet());
        List<ParqueaderoVisitante> lista = inmuebles.findByActivo(true).stream()
                .filter(this::esDeVisitantes)
                .sorted(Comparator.comparing(Inmueble::getIdentificacion))
                .map(i -> new ParqueaderoVisitante(i.getId(), i.getIdentificacion(), ocupados.contains(i.getId())))
                .toList();
        int oc = (int) lista.stream().filter(ParqueaderoVisitante::ocupado).count();
        return new DisponibilidadVisitantes(lista.size(), oc, lista.size() - oc, lista);
    }

    // RF-055: historial de ingresos y salidas de un vehículo (solo administrador por SecurityConfig).
    @GetMapping("/api/accesos/vehiculos/{vehiculoId}/historial")
    @Transactional(readOnly = true)
    public List<MovimientoDto> historial(
            @PathVariable Long vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        if (!vehiculos.existsById(vehiculoId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehículo no encontrado");
        }
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        return movimientos.findByVehiculoIdAndIngresoEnBetweenOrderByIngresoEnDesc(
                        vehiculoId, inicio.atStartOfDay(), fin.atTime(LocalTime.MAX))
                .stream().map(this::dto).toList();
    }

    // ---------- Ayudas ----------

    private Vehiculo buscarActivoPorPlaca(String placa) {
        return vehiculos.findAll().stream()
                .filter(v -> v.isActivo() && v.getPlaca().equals(placa))
                .findFirst().orElse(null);
    }

    private Inmueble validarParqueadero(Long id, boolean visitante) {
        Inmueble p = inmuebles.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Parqueadero no encontrado"));
        if (!esParqueadero(p)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ese inmueble no es un parqueadero");
        }
        if (!p.isActivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parqueadero está desactivado");
        }
        if (movimientos.existsByParqueaderoIdAndSalidaEnIsNull(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El parqueadero ya está ocupado por otro vehículo");
        }
        if (visitante && !esDeVisitantes(p)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un vehículo de visitante solo puede usar un parqueadero de visitantes");
        }
        return p;
    }

    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    private boolean esParqueadero(Inmueble i) {
        return i.getTipoInmueble() != null && norm(i.getTipoInmueble().getNombre()).startsWith("parqueadero");
    }

    private boolean esDeVisitantes(Inmueble i) {
        return esParqueadero(i) && norm(i.getTipoInmueble().getNombre()).contains("visitante");
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

    private String nombreVigilante(Authentication auth) {
        return usuarios.buscarConIntegrante(auth.getName())
                .map(u -> u.getIntegrante().getNombres() + " " + u.getIntegrante().getApellidos())
                .orElse(auth.getName());
    }

    private MovimientoDto dto(MovimientoVehicular m) {
        Vehiculo v = m.getVehiculo();
        Inmueble p = m.getParqueadero();
        return new MovimientoDto(m.getId(), m.getPlaca(), v == null,
                v == null ? null : v.getMarca() + " " + v.getColor(),
                m.getInmueble().getId(), m.getInmueble().getIdentificacion(),
                p == null ? null : p.getId(), p == null ? null : p.getIdentificacion(),
                m.getIngresoEn(), m.getSalidaEn(),
                m.getVigilanteIngreso(), m.getVigilanteSalida(), m.getSalidaEn() == null);
    }
}