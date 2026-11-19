package com.proyecto.plataforma_ph.security;

import com.proyecto.plataforma_ph.model.RegistroAuditoria;
import com.proyecto.plataforma_ph.repository.RegistroAuditoriaRepository;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private final RegistroAuditoriaRepository repo;

    public AuditoriaService(RegistroAuditoriaRepository repo) {
        this.repo = repo;
    }

    // Se une a la transacción de quien lo llama: si la operación falla, el registro no queda.
    @Transactional
    public void registrar(String accion, String entidad, Long entidadId, String detalle) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.setFechaHora(LocalDateTime.now());
        r.setUsuario(usuarioActual());
        r.setAccion(accion);
        r.setEntidad(entidad);
        r.setEntidadId(entidadId);
        r.setDetalle(detalle);
        repo.save(r);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "sistema";
    }
}