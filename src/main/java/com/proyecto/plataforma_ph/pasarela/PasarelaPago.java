package com.proyecto.plataforma_ph.pasarela;

import com.proyecto.plataforma_ph.model.Pago;

/**
 * Servicio de pagos externo (RNF-13). Hoy hay una implementación simulada;
 * una pasarela real (por ejemplo Wompi) se agrega como otra clase que la implemente.
 * El flujo es el de una pasarela real: iniciar -> el usuario paga allá -> el sistema consulta el resultado.
 */
public interface PasarelaPago {

    /** Dirección a la que se envía al usuario para pagar. */
    record SesionPago(String url) {}

    enum Estado { PENDIENTE, APROBADA, RECHAZADA }

    record ResultadoPasarela(Estado estado, String transaccionId) {}

    String nombre();

    SesionPago iniciar(Pago pago, String urlRetorno);

    ResultadoPasarela consultar(Pago pago);
}