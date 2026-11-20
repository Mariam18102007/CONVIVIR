package com.proyecto.plataforma_ph.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Envío de correos: sin SMTP no falla, con SMTP envía y un error del servidor no se propaga. */
class CorreoServiceTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> proveedor(JavaMailSender s) {
        ObjectProvider<JavaMailSender> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(s);
        return p;
    }

    @Test
    void sinSmtpConfiguradoNoFallaYNoEnvia() {
        CorreoService c = new CorreoService(proveedor(null), "a@b.co", true);
        assertDoesNotThrow(() -> c.enviar("x@y.co", "Asunto", "Texto"));
    }

    @Test
    void sinSmtpYSinConsolaTampocoFalla() {
        CorreoService c = new CorreoService(proveedor(null), "a@b.co", false);
        assertDoesNotThrow(() -> c.enviar("x@y.co", "Asunto", "Texto"));
    }

    @Test
    void conSmtpEnviaElMensaje() {
        JavaMailSender s = mock(JavaMailSender.class);
        new CorreoService(proveedor(s), "a@b.co", false).enviar("x@y.co", "Asunto", "Texto");
        verify(s).send(any(SimpleMailMessage.class));
    }

    @Test
    void unFalloDelServidorNoSePropaga() {
        JavaMailSender s = mock(JavaMailSender.class);
        doThrow(new MailSendException("caído")).when(s).send(any(SimpleMailMessage.class));
        CorreoService c = new CorreoService(proveedor(s), "a@b.co", false);
        assertDoesNotThrow(() -> c.enviar("x@y.co", "Asunto", "Texto"));
    }
}