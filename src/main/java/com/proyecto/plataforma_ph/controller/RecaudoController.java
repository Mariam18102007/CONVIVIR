package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.CanalPago;
import com.proyecto.plataforma_ph.model.EstadoPago;
import com.proyecto.plataforma_ph.model.MedioPago;
import com.proyecto.plataforma_ph.model.Pago;
import com.proyecto.plataforma_ph.model.PagoDetalle;
import com.proyecto.plataforma_ph.repository.PagoRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF-088: total recaudado en un periodo, diferenciado por canal. Solo lectura (RN-31).
 * Solo cuenta pagos confirmados y no entrega datos de personas (RN-33).
 */
@RestController
@RequestMapping("/api/recaudo")
public class RecaudoController {

    public record Linea(String clave, String etiqueta, long pagos, long total) {}

    public record RecaudoDto(LocalDate desde, LocalDate hasta, long pagos, long total,
                             List<Linea> porCanal, List<Linea> porMedio) {}

    private final PagoRepository pagos;

    public RecaudoController(PagoRepository pagos) {
        this.pagos = pagos;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public RecaudoDto recaudo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDate hoy = LocalDate.now();
        LocalDate d = desde != null ? desde : hoy.withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : YearMonth.from(hoy).atEndOfMonth();
        if (h.isBefore(d)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha final no puede ser anterior a la inicial");
        }

        Map<CanalPago, long[]> canales = new EnumMap<>(CanalPago.class);
        for (CanalPago c : CanalPago.values()) canales.put(c, new long[2]);
        Map<MedioPago, long[]> medios = new EnumMap<>(MedioPago.class);
        for (MedioPago m : MedioPago.values()) medios.put(m, new long[2]);

        long totalPagos = 0;
        long total = 0;
        for (Pago p : pagos.findAllByOrderByIdDesc()) {
            if (p.getEstado() != EstadoPago.CONFIRMADO) continue;
            if (p.getFecha().isBefore(d) || p.getFecha().isAfter(h)) continue;
            long valor = 0;
            for (PagoDetalle x : p.getDetalles()) valor += x.getValor();
            totalPagos++;
            total += valor;
            long[] c = canales.get(p.getCanal());
            c[0]++;
            c[1] += valor;
            long[] m = medios.get(p.getMedio());
            m[0]++;
            m[1] += valor;
        }

        List<Linea> porCanal = new ArrayList<>();
        for (CanalPago c : CanalPago.values()) {
            long[] v = canales.get(c);
            porCanal.add(new Linea(c.name(), c == CanalPago.PASARELA ? "Pasarela" : "Presencial", v[0], v[1]));
        }
        List<Linea> porMedio = new ArrayList<>();
        for (MedioPago m : MedioPago.values()) {
            long[] v = medios.get(m);
            String etiqueta = switch (m) {
                case EFECTIVO -> "Efectivo";
                case TRANSFERENCIA -> "Transferencia";
                case CHEQUE -> "Cheque";
                case PASARELA -> "Pasarela de pagos";
            };
            porMedio.add(new Linea(m.name(), etiqueta, v[0], v[1]));
        }
        return new RecaudoDto(d, h, totalPagos, total, porCanal, porMedio);
    }
}