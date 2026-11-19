package com.proyecto.plataforma_ph.service;

import com.proyecto.plataforma_ph.model.EstadoObligacion;
import com.proyecto.plataforma_ph.model.EventoNotificacion;
import com.proyecto.plataforma_ph.model.Obligacion;
import com.proyecto.plataforma_ph.repository.ObligacionRepository;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** CU-17: avisos de vencimiento. Corre una vez al día, así que cada caso se avisa una sola vez. */
@Component
public class VencimientosScheduler {

    static final int DIAS_ANTICIPACION = 3;

    private final ObligacionRepository obligaciones;
    private final NotificacionService notificaciones;

    public VencimientosScheduler(ObligacionRepository obligaciones, NotificacionService notificaciones) {
        this.obligaciones = obligaciones;
        this.notificaciones = notificaciones;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void revisar() {
        LocalDate hoy = LocalDate.now();
        for (Obligacion o : obligaciones.findAllByOrderByIdDesc()) {
            if (o.getEstado() != EstadoObligacion.PENDIENTE || o.getSaldo() <= 0) continue;

            if (o.getFechaVencimiento().equals(hoy.plusDays(DIAS_ANTICIPACION))) {
                notificaciones.avisar(EventoNotificacion.VENCIMIENTO_PROXIMO, o.getInmueble(),
                        "Una obligación está por vencer",
                        texto(o) + " vence el " + o.getFechaVencimiento() + ".");
            } else if (o.getFechaVencimiento().equals(hoy.minusDays(1))) {
                notificaciones.avisar(EventoNotificacion.OBLIGACION_VENCIDA, o.getInmueble(),
                        "Una obligación está vencida",
                        texto(o) + " venció el " + o.getFechaVencimiento() + ".");
            }
        }
    }

    private String texto(Obligacion o) {
        return o.getConcepto().getEtiqueta() + " " + o.getPeriodo() + " (inmueble "
                + o.getInmueble().getIdentificacion() + ", saldo $ " + o.getSaldo() + ")";
    }
}