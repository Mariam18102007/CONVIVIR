package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.PeriodoFacturacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodoFacturacionRepository extends JpaRepository<PeriodoFacturacion, Long> {

    Optional<PeriodoFacturacion> findByPeriodo(String periodo);

    List<PeriodoFacturacion> findAllByOrderByPeriodoDesc();
}