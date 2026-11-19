package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Autorizacion;
import com.proyecto.plataforma_ph.model.TipoAutorizacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutorizacionRepository extends JpaRepository<Autorizacion, Long> {

    List<Autorizacion> findByAutorizanteIdAndTipoOrderByIdDesc(Long autorizanteId, TipoAutorizacion tipo);

    List<Autorizacion> findByInmuebleIdAndTipoOrderByIdDesc(Long inmuebleId, TipoAutorizacion tipo);

    List<Autorizacion> findByDocumentoOrderByIdDesc(String documento);

    List<Autorizacion> findByInmuebleIdOrderByIdDesc(Long inmuebleId);
}