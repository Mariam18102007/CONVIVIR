package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.EstadoReserva;
import com.proyecto.plataforma_ph.model.Reserva;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findAllByOrderByIdDesc();

    List<Reserva> findBySolicitanteIdOrderByIdDesc(Long solicitanteId);

    List<Reserva> findByZonaIdAndEstado(Long zonaId, EstadoReserva estado);
}