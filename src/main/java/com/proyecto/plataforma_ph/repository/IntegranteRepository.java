package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Integrante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegranteRepository extends JpaRepository<Integrante, Long> {

    Optional<Integrante> findByNumeroDocumento(String numeroDocumento);

    boolean existsByNumeroDocumento(String numeroDocumento);

    List<Integrante> findByApellidosContainingIgnoreCase(String apellidos);
}
