package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    boolean existsByPlacaAndActivoTrue(String placa);

    boolean existsByPlacaAndActivoTrueAndIdNot(String placa, Long id);
}