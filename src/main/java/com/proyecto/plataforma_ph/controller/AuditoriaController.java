package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.model.RegistroAuditoria;
import com.proyecto.plataforma_ph.repository.RegistroAuditoriaRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final RegistroAuditoriaRepository repo;
    private final AuditoriaService auditoria;

    public AuditoriaController(RegistroAuditoriaRepository repo, AuditoriaService auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    // RF-108: consultar filtrando por usuario, fecha o tipo de operación.
    // Fechas en formato AAAA-MM-DD. Si no se indican, se toman los últimos 30 días.
    @GetMapping
    @Transactional(readOnly = true)
    public List<RegistroAuditoria> consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion) {
        return filtrar(desde, hasta, usuario, accion);
    }

    // RF-109: exportar el registro de un periodo (CSV, se abre en Excel).
    @GetMapping("/exportar")
    @Transactional
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<RegistroAuditoria> filas = filtrar(desde, hasta, null, null);

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("fecha_hora;usuario;accion;entidad;entidad_id;detalle\n");
        for (RegistroAuditoria r : filas) {
            sb.append(csv(String.valueOf(r.getFechaHora()))).append(';')
              .append(csv(r.getUsuario())).append(';')
              .append(csv(r.getAccion())).append(';')
              .append(csv(r.getEntidad())).append(';')
              .append(csv(r.getEntidadId() == null ? "" : r.getEntidadId().toString())).append(';')
              .append(csv(r.getDetalle())).append('\n');
        }

        auditoria.registrar("EXPORTAR", "Auditoria", null, filas.size() + " registros");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=auditoria.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<RegistroAuditoria> filtrar(LocalDate desde, LocalDate hasta, String usuario, String accion) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);

        return repo.findByFechaHoraBetweenOrderByFechaHoraDesc(
                        inicio.atStartOfDay(), fin.atTime(LocalTime.MAX)).stream()
                .filter(r -> usuario == null || usuario.isBlank() || r.getUsuario().equalsIgnoreCase(usuario))
                .filter(r -> accion == null || accion.isBlank() || r.getAccion().equalsIgnoreCase(accion))
                .toList();
    }

    private String csv(String valor) {
        if (valor == null) return "";
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}