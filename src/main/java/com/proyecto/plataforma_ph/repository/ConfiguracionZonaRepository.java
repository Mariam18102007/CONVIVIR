package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.ConfiguracionZona;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionZonaRepository extends JpaRepository<ConfiguracionZona, Long> {

    Optional<ConfiguracionZona> findByZonaId(Long zonaId);
}