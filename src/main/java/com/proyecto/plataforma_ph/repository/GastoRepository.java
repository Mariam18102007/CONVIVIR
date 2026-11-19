package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Gasto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    List<Gasto> findAllByOrderByFechaDescIdDesc();
}