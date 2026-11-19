package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Reporte;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    List<Reporte> findAllByOrderByIdDesc();

    List<Reporte> findByCreadorIdOrderByIdDesc(Long creadorId);

    List<Reporte> findByInmuebleIdInOrderByIdDesc(Collection<Long> inmuebleIds);
}