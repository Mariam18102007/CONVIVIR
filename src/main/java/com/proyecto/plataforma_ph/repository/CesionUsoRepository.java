package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.CesionUso;
import com.proyecto.plataforma_ph.model.EstadoCesion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CesionUsoRepository extends JpaRepository<CesionUso, Long> {

    List<CesionUso> findByEstado(EstadoCesion estado);

    List<CesionUso> findByParqueaderoIdAndEstado(Long parqueaderoId, EstadoCesion estado);

    List<CesionUso> findByParqueaderoIdOrderByIdDesc(Long parqueaderoId);
}
