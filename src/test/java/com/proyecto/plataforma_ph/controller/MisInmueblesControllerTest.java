package com.proyecto.plataforma_ph.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.proyecto.plataforma_ph.controller.MisInmueblesController.MiInmueble;
import com.proyecto.plataforma_ph.model.EstadoVinculacion;
import com.proyecto.plataforma_ph.model.Inmueble;
import com.proyecto.plataforma_ph.model.Integrante;
import com.proyecto.plataforma_ph.model.Naturaleza;
import com.proyecto.plataforma_ph.model.TipoInmueble;
import com.proyecto.plataforma_ph.model.TipoVinculo;
import com.proyecto.plataforma_ph.model.Usuario;
import com.proyecto.plataforma_ph.model.Vinculacion;
import com.proyecto.plataforma_ph.repository.UsuarioRepository;
import com.proyecto.plataforma_ph.repository.VinculacionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** RF-026 y RN-07: cada residente ve solo sus vinculaciones vigentes. */
class MisInmueblesControllerTest {

    private UsuarioRepository usuarios;
    private VinculacionRepository vinculaciones;
    private MisInmueblesController controller;
    private Authentication auth;
    private Integrante yo;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        vinculaciones = mock(VinculacionRepository.class);
        controller = new MisInmueblesController(usuarios, vinculaciones);

        yo = new Integrante();
        ReflectionTestUtils.setField(yo, "id", 5L);
        Usuario u = new Usuario();
        u.setCorreo("pepe@ph.local");
        u.setIntegrante(yo);

        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("pepe@ph.local");
        when(usuarios.buscarConIntegrante("pepe@ph.local")).thenReturn(Optional.of(u));
    }

    private Vinculacion vinculacion(long id, String identificacion, TipoVinculo tipo) {
        TipoInmueble tipoInmueble = new TipoInmueble();
        tipoInmueble.setNombre("Apartamento");

        Inmueble i = new Inmueble();
        ReflectionTestUtils.setField(i, "id", id * 10);
        i.setIdentificacion(identificacion);
        i.setNaturaleza(Naturaleza.BIEN_PRIVADO);
        i.setTipoInmueble(tipoInmueble);

        Vinculacion v = new Vinculacion();
        ReflectionTestUtils.setField(v, "id", id);
        v.setInmueble(i);
        v.setIntegrante(yo);
        v.setTipoVinculo(tipo);
        v.setFechaInicio(LocalDate.of(2026, 1, 15));
        return v;
    }

    @Test
    void listaSusVinculacionesVigentesOrdenadasPorInmueble() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of(
                vinculacion(2, "Apto 302", TipoVinculo.ARRENDATARIO),
                vinculacion(1, "Apto 101", TipoVinculo.PROPIETARIO_RESIDENTE)));

        List<MiInmueble> lista = controller.mias(auth);

        assertEquals(2, lista.size());
        assertEquals("Apto 101", lista.get(0).identificacion());
        assertEquals("PROPIETARIO_RESIDENTE", lista.get(0).tipoVinculo());
        assertEquals("BIEN_PRIVADO", lista.get(0).naturaleza());
        assertEquals("Apartamento", lista.get(0).tipoInmueble());
        assertEquals(LocalDate.of(2026, 1, 15), lista.get(0).fechaInicio());
        assertEquals("Apto 302", lista.get(1).identificacion());
        assertEquals("ARRENDATARIO", lista.get(1).tipoVinculo());
    }

    @Test
    void sinVinculacionesDevuelveListaVacia() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());
        assertTrue(controller.mias(auth).isEmpty());
    }

    @Test
    void soloConsultaVinculacionesDelUsuarioAutenticado() {
        when(vinculaciones.findByIntegranteIdAndEstado(5L, EstadoVinculacion.VIGENTE)).thenReturn(List.of());
        when(vinculaciones.findByIntegranteIdAndEstado(99L, EstadoVinculacion.VIGENTE))
                .thenReturn(List.of(vinculacion(9, "Apto 999", TipoVinculo.PROPIETARIO_NO_RESIDENTE)));

        // El usuario autenticado es el integrante 5: nunca debe ver lo del integrante 99.
        assertTrue(controller.mias(auth).isEmpty());
    }

    @Test
    void cuentaInexistenteRecibeProhibido() {
        when(usuarios.buscarConIntegrante("pepe@ph.local")).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> controller.mias(auth));
        assertEquals(HttpStatus.FORBIDDEN, HttpStatus.valueOf(e.getStatusCode().value()));
    }
}
