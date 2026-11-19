package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByIntegranteId(Long integranteId);

    // Trae el usuario junto con su integrante (para leer el rol sin errores de carga perezosa).
    @Query("select u from Usuario u join fetch u.integrante where lower(u.correo) = lower(:correo)")
    Optional<Usuario> buscarConIntegrante(@Param("correo") String correo);
}