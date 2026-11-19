package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.CuentaController.CambiarPassword;
import com.proyecto.plataforma_ph.controller.CuentaController.Perfil;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.repository.IntegranteRepository;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.security.AuditoriaService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** RF-003 (cambiar contraseña) y RF-005 (datos de contacto propios). */
class CuentaControllerTest {

    private UsuarioRepository usuarios;
    private IntegranteRepository integrantes;
    private AuditoriaService auditoria;
    private PasswordEncoder encoder;
    private CuentaController controller;
    private Authentication auth;
    private Usuario usuario;
    private Integrante integrante;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        integrantes = mock(IntegranteRepository.class);
        auditoria = mock(AuditoriaService.class);
        encoder = new BCryptPasswordEncoder();
        controller = new CuentaController(usuarios, integrantes, encoder, auditoria);

        integrante = new Integrante();
        ReflectionTestUtils.setField(integrante, "id", 7L);
        integrante.setNombres("Ana");
        integrante.setApellidos("Pérez");
        integrante.setTelefono("3001234567");
        integrante.setCorreo("ana@correo.com");

        usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", 1L);
        usuario.setCorreo("ana@ph.local");
        usuario.setPasswordHash(encoder.encode("clave-actual-1"));
        usuario.setIntegrante(integrante);

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ana@ph.local");
        when(usuarios.buscarConIntegrante("ana@ph.local")).thenReturn(Optional.of(usuario));
        when(usuarios.findByCorreoIgnoreCase("ana@ph.local")).thenReturn(Optional.of(usuario));
    }

    private static HttpStatus estado(ResponseStatusException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    // ---------- RF-005 ----------

    @Test
    void consultaSusPropiosDatos() {
        Perfil p = controller.perfil(auth);
        assertEquals("Ana", p.nombres());
        assertEquals("Pérez", p.apellidos());
        assertEquals("3001234567", p.telefono());
        assertEquals("ana@correo.com", p.correo());
    }

    @Test
    void modificaSusDatosYRegistraAuditoria() {
        Perfil nuevo = controller.modificarPerfil(
                new Perfil("  María ", " Gómez ", "+57 300 111 2233", " maria@correo.com "), auth);

        assertEquals("María", nuevo.nombres());
        assertEquals("Gómez", nuevo.apellidos());
        assertEquals("+57 300 111 2233", integrante.getTelefono());
        assertEquals("maria@correo.com", integrante.getCorreo());
        verify(integrantes).save(integrante);
        verify(auditoria).registrar(eq("MODIFICAR"), eq("Integrante"), eq(7L), any());
    }

    @Test
    void elLoginNoCambiaAlModificarElCorreoDeContacto() {
        controller.modificarPerfil(new Perfil("Ana", "Pérez", null, "otro@correo.com"), auth);
        assertEquals("ana@ph.local", usuario.getCorreo());
    }

    @Test
    void telefonoYCorreoSonOpcionales() {
        Perfil p = controller.modificarPerfil(new Perfil("Ana", "Pérez", "  ", null), auth);
        assertNull(p.telefono());
        assertNull(p.correo());
    }

    @Test
    void rechazaNombreOApellidoVacios() {
        ResponseStatusException e1 = assertThrows(ResponseStatusException.class,
                () -> controller.modificarPerfil(new Perfil(" ", "Pérez", null, null), auth));
        ResponseStatusException e2 = assertThrows(ResponseStatusException.class,
                () -> controller.modificarPerfil(new Perfil("Ana", null, null, null), auth));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e1));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e2));
        verify(integrantes, never()).save(any());
    }

    @Test
    void rechazaCorreoInvalido() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.modificarPerfil(new Perfil("Ana", "Pérez", null, "no-es-correo"), auth));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e));
        assertEquals("ana@correo.com", integrante.getCorreo());
    }

    @Test
    void rechazaTelefonoInvalido() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.modificarPerfil(new Perfil("Ana", "Pérez", "abc", null), auth));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e));
        assertEquals("3001234567", integrante.getTelefono());
    }

    @Test
    void cuentaInexistenteNoPuedeVerNiModificarPerfil() {
        when(usuarios.buscarConIntegrante("ana@ph.local")).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.perfil(auth));
        assertEquals(HttpStatus.FORBIDDEN, estado(e));
    }

    // ---------- RF-003 ----------

    @Test
    void cambiaLaContrasenaConLaActualCorrecta() {
        controller.cambiarPassword(new CambiarPassword("clave-actual-1", "clave-nueva-2"), auth);
        assertEquals(true, encoder.matches("clave-nueva-2", usuario.getPasswordHash()));
        verify(auditoria).registrar("CAMBIAR_PASSWORD", "Usuario", 1L, null);
    }

    @Test
    void rechazaContrasenaActualIncorrecta() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> controller.cambiarPassword(new CambiarPassword("equivocada", "clave-nueva-2"), auth));
        assertEquals(HttpStatus.BAD_REQUEST, estado(e));
        assertEquals(true, encoder.matches("clave-actual-1", usuario.getPasswordHash()));
    }

    @Test
    void rechazaContrasenaNuevaCortaOIgualAlaActual() {
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class,
                () -> controller.cambiarPassword(new CambiarPassword("clave-actual-1", "corta"), auth))));
        assertEquals(HttpStatus.BAD_REQUEST, estado(assertThrows(ResponseStatusException.class,
                () -> controller.cambiarPassword(new CambiarPassword("clave-actual-1", "clave-actual-1"), auth))));
    }
}
