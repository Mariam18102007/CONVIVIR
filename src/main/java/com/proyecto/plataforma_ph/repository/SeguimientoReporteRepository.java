package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.SeguimientoReporte;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoReporteRepository extends JpaRepository<SeguimientoReporte, Long> {

    List<SeguimientoReporte> findByReporteIdOrderByIdAsc(Long reporteId);
}