package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Notificacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findTop200ByOrderByIdDesc();

    List<Notificacion> findTop200ByEventoOrderByIdDesc(EventoNotificacion evento);

    List<Notificacion> findTop100ByDestinatarioIdOrderByIdDesc(Long destinatarioId);

    List<Notificacion> findByDestinatarioIdAndLeidaFalse(Long destinatarioId);
}