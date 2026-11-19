package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Naturaleza;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InmuebleRepository extends JpaRepository<Inmueble, Long> {

    long countByTipoInmuebleIdAndActivoTrue(Long tipoInmuebleId);

    List<Inmueble> findByTipoInmuebleId(Long tipoInmuebleId);

    List<Inmueble> findByNaturaleza(Naturaleza naturaleza);

    List<Inmueble> findByActivo(boolean activo);

    List<Inmueble> findByContenedorId(Long contenedorId);
}