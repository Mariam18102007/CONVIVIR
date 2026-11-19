package com.proyecto.plataforma_ph.repository;

import com.proyecto.plataforma_ph.model.Pago;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findAllByOrderByIdDesc();

    List<Pago> findByInmuebleIdOrderByIdDesc(Long inmuebleId);

    List<Pago> findByInmuebleIdInOrderByIdDesc(Collection<Long> inmuebleIds);

    Optional<Pago> findByReferencia(String referencia);
}
