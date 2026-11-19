package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.ConceptoObligacion;
import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.Obligacion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {

    List<Obligacion> findAllByOrderByIdDesc();

    List<Obligacion> findByInmuebleIdOrderByIdDesc(Long inmuebleId);

    List<Obligacion> findByInmuebleIdInOrderByIdDesc(Collection<Long> inmuebleIds);

    boolean existsByInmuebleIdAndConceptoAndPeriodoAndEstadoNot(
            Long inmuebleId, ConceptoObligacion concepto, String periodo, EstadoObligacion estado);
}