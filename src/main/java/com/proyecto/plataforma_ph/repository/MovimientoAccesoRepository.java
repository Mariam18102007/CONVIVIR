package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.MovimientoAcceso;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoAccesoRepository extends JpaRepository<MovimientoAcceso, Long> {

    List<MovimientoAcceso> findBySalidaEnIsNullOrderByIngresoEnDesc();

    boolean existsByAutorizacionIdAndSalidaEnIsNull(Long autorizacionId);

    List<MovimientoAcceso> findByIngresoEnBetweenOrderByIngresoEnDesc(LocalDateTime desde, LocalDateTime hasta);

    List<MovimientoAcceso> findByInmuebleIdAndIngresoEnBetweenOrderByIngresoEnDesc(
            Long inmuebleId, LocalDateTime desde, LocalDateTime hasta);
}