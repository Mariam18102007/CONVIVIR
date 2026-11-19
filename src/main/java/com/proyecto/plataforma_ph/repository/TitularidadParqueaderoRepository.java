package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.TitularidadParqueadero;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitularidadParqueaderoRepository extends JpaRepository<TitularidadParqueadero, Long> {

    Optional<TitularidadParqueadero> findByParqueaderoIdAndFechaFinIsNull(Long parqueaderoId);

    List<TitularidadParqueadero> findByFechaFinIsNull();
}
