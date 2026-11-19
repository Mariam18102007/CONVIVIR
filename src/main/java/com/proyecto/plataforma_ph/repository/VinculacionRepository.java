package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Vinculacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VinculacionRepository extends JpaRepository<Vinculacion, Long> {

    List<Vinculacion> findByInmuebleIdAndEstado(Long inmuebleId, EstadoVinculacion estado);

    List<Vinculacion> findByInmuebleIdOrderByFechaInicioDescIdDesc(Long inmuebleId);

    List<Vinculacion> findByIntegranteIdAndEstado(Long integranteId, EstadoVinculacion estado);

    List<Vinculacion> findByInmuebleIdAndEstadoAndTipoVinculoIn(
            Long inmuebleId, EstadoVinculacion estado, Collection<TipoVinculo> tipos);
}
