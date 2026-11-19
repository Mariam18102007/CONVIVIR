package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.MovimientoVehicular;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoVehicularRepository extends JpaRepository<MovimientoVehicular, Long> {

    List<MovimientoVehicular> findBySalidaEnIsNullOrderByIngresoEnDesc();

    List<MovimientoVehicular> findByInmuebleIdOrderByIngresoEnDesc(Long inmuebleId);

    boolean existsByPlacaAndSalidaEnIsNull(String placa);

    boolean existsByParqueaderoIdAndSalidaEnIsNull(Long parqueaderoId);

    List<MovimientoVehicular> findByVehiculoIdAndIngresoEnBetweenOrderByIngresoEnDesc(
            Long vehiculoId, LocalDateTime desde, LocalDateTime hasta);
}