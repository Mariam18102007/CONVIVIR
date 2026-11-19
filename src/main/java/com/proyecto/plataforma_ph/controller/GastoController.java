package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.Gasto;
import com.proyecto.plataforma_ph.repository.GastoRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gastos de la copropiedad. Consultar (RF-089): administrador y consejo (SecurityConfig).
 * Registrar y anular: solo el administrador (cae en /api/**).
 */
@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    private static final long MAX_VALOR = 1_000_000_000_000L;

    public record NuevoGasto(String concepto, Long valor, LocalDate fecha) {}

    public record GastoDto(Long id, String concepto, long valor, LocalDate fecha,
                           boolean anulado, String motivoAnulacion) {}

    private final GastoRepository gastos;
    private final AuditoriaService auditoria;

    public GastoController(GastoRepository gastos, AuditoriaService auditoria) {
        this.gastos = gastos;
        this.auditoria = auditoria;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<GastoDto> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return gastos.findAllByOrderByFechaDescIdDesc().stream()
                .filter(g -> desde == null || !g.getFecha().isBefore(desde))
                .filter(g -> hasta == null || !g.getFecha().isAfter(hasta))
                .map(this::dto)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public GastoDto crear(@RequestBody NuevoGasto body, Authentication auth) {
        if (body.concepto() == null || body.concepto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el concepto del gasto");
        }
        String concepto = body.concepto().trim();
        if (concepto.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El concepto no puede pasar de 120 caracteres");
        }
        if (body.valor() == null || body.valor() <= 0 || body.valor() > MAX_VALOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor debe ser mayor que cero");
        }
        LocalDate fecha = body.fecha() != null ? body.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha del gasto no puede ser futura");
        }
        Gasto g = new Gasto();
        g.setConcepto(concepto);
        g.setValor(body.valor());
        g.setFecha(fecha);
        g.setRegistradoPor(auth.getName());
        gastos.save(g);
        auditoria.registrar("REGISTRAR_GASTO", "Gasto", g.getId(), "Valor " + g.getValor());
        return dto(g);
    }

    @PatchMapping("/{id}/anular")
    @Transactional
    public GastoDto anular(@PathVariable Long id, @RequestParam String motivo) {
        Gasto g = gastos.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique el motivo de la anulación");
        }
        String m = motivo.trim();
        if (m.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo no puede pasar de 200 caracteres");
        }
        if (g.isAnulado()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El gasto ya está anulado");
        }
        g.setAnulado(true);
        g.setMotivoAnulacion(m);
        g.setAnuladoEn(LocalDateTime.now());
        gastos.save(g);
        auditoria.registrar("ANULAR_GASTO", "Gasto", g.getId(), "Motivo: " + m);
        return dto(g);
    }

    private GastoDto dto(Gasto g) {
        return new GastoDto(g.getId(), g.getConcepto(), g.getValor(), g.getFecha(),
                g.isAnulado(), g.getMotivoAnulacion());
    }
}
