package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.ConfiguracionNotificacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionNotificacionRepository extends JpaRepository<ConfiguracionNotificacion, Long> {

    Optional<ConfiguracionNotificacion> findByEvento(EventoNotificacion evento);
}