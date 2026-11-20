package com.proyecto.plataforma_ph.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Envío de correos (RNF-13). Un fallo nunca afecta a quien lo llama. */
@Service
public class CorreoService {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String remitente;
    private final boolean consola;

    public CorreoService(ObjectProvider<JavaMailSender> sender,
                         @Value("${app.mail.from}") String remitente,
                         @Value("${app.mail.consola:true}") boolean consola) {
        this.sender = sender;
        this.remitente = remitente;
        this.consola = consola;
    }

    @Async
    public void enviar(String para, String asunto, String texto) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            if (consola) {
                LOG.warn(">>> Correo no configurado. Mensaje para {}: {}\n{}", para, asunto, texto);
            } else {
                LOG.warn("Correo no configurado: no se envió '{}' a {}", asunto, para);
            }
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(remitente);
            m.setTo(para);
            m.setSubject(asunto);
            m.setText(texto);
            s.send(m);
        } catch (MailException e) {
            LOG.warn("No se pudo enviar '{}' a {}: {}", asunto, para, e.getMessage());
        }
    }
}