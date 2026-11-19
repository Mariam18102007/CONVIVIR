package com.proyecto.plataforma_ph.pasarela;

import com.proyecto.plataforma_ph.model.Pago;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pasarela de pruebas: el "banco" es una página propia (/pasarela-simulada/...) donde la persona
 * aprueba o rechaza. El resultado se guarda en el pago y se consulta como si viniera de un tercero.
 */
@Component
public class PasarelaSimulada implements PasarelaPago {

    @Override
    public String nombre() {
        return "Pasarela simulada";
    }

    @Override
    public SesionPago iniciar(Pago pago, String urlRetorno) {
        return new SesionPago("/pasarela-simulada/" + pago.getReferencia());
    }

    @Override
    public ResultadoPasarela consultar(Pago pago) {
        String e = pago.getEstadoPasarela();
        if ("APROBADA".equals(e)) {
            return new ResultadoPasarela(Estado.APROBADA, pago.getTransaccionId());
        }
        if ("RECHAZADA".equals(e)) {
            return new ResultadoPasarela(Estado.RECHAZADA, pago.getTransaccionId());
        }
        return new ResultadoPasarela(Estado.PENDIENTE, null);
    }

    /** Solo de la simulación: lo que la persona decide en la página de la pasarela. */
    public void responder(Pago pago, boolean aprobado) {
        pago.setEstadoPasarela(aprobado ? "APROBADA" : "RECHAZADA");
        pago.setTransaccionId("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}