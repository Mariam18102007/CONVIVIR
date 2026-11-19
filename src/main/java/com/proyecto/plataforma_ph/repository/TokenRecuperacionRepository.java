package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.TokenRecuperacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    List<TokenRecuperacion> findByUsuarioIdAndUsadoFalse(Long usuarioId);
}