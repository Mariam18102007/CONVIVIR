package com.proyecto.plataforma_ph.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Matriz de permisos por rol (RNF-02, RN-07, RN-13, RN-31). Recorre la configuración de
 * seguridad real: cada rol que NO debe entrar recibe 403 antes de llegar al controlador,
 * así que estas pruebas no modifican datos. Para los roles que sí deben entrar solo se
 * consultan listados, y se comprueba que la seguridad los deja pasar (ni 401 ni 403).
 */
@SpringBootTest
class PermisosPorRolTest {

    private static final String ADMIN = "ADMINISTRADOR";
    private static final String CONSEJO = "MIEMBRO_CONSEJO";
    private static final String VIGILANTE = "VIGILANTE";
    private static final String PROPIETARIO = "PROPIETARIO";
    private static final String ARRENDATARIO = "ARRENDATARIO";
    private static final String RESIDENTE = "RESIDENTE_AUTORIZADO";

    private static final List<String> TODOS =
            List.of(ADMIN, CONSEJO, VIGILANTE, PROPIETARIO, ARRENDATARIO, RESIDENTE);

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private int estado(HttpMethod metodo, String ruta, String rol) throws Exception {
        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(metodo, ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (rol != null) {
            peticion = peticion.with(user("prueba@ph.local").roles(rol));
        }
        return mvc.perform(peticion).andReturn().getResponse().getStatus();
    }

    /** Todos los roles de la lista deben recibir 403 en esa ruta. */
    private void prohibidoPara(HttpMethod metodo, String ruta, String... roles) throws Exception {
        for (String rol : roles) {
            assertEquals(403, estado(metodo, ruta, rol),
                    rol + " no debería poder " + metodo + " " + ruta);
        }
    }

    /** La seguridad debe dejar pasar a esos roles (el controlador puede responder lo que quiera). */
    private void permitidoPara(HttpMethod metodo, String ruta, String... roles) throws Exception {
        for (String rol : roles) {
            int codigo = estado(metodo, ruta, rol);
            assertTrue(codigo != 401 && codigo != 403,
                    rol + " debería poder " + metodo + " " + ruta + " pero recibió " + codigo);
        }
    }

    private static String[] excepto(String... permitidos) {
        List<String> ok = List.of(permitidos);
        return TODOS.stream().filter(r -> !ok.contains(r)).toArray(String[]::new);
    }

    // ---------- sin sesión ----------

    @Test
    void sinSesionNoSePuedeUsarLaApi() throws Exception {
        for (String ruta : List.of("/api/pagos", "/api/cartera", "/api/auditoria", "/api/inmuebles")) {
            int codigo = estado(HttpMethod.GET, ruta, null);
            assertTrue(codigo == 401 || codigo == 302,
                    "Sin sesión " + ruta + " debería pedir autenticación, pero recibió " + codigo);
        }
    }

    // ---------- pagos (CU-12 y CU-13) ----------

    @Test
    void soloElAdministradorGestionaPagosPresencialesYListados() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/pagos", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/pagos/presencial", excepto(ADMIN));
        prohibidoPara(HttpMethod.PATCH, "/api/pagos/1/anular?motivo=prueba", excepto(ADMIN));
        permitidoPara(HttpMethod.GET, "/api/pagos", ADMIN);
    }

    @Test
    void soloPropietarioYArrendatarioPaganPorPasarela() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/pagos/pasarela", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.POST, "/api/pagos/1/verificar", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.GET, "/api/pagos/mios", excepto(PROPIETARIO, ARRENDATARIO));
    }

    @Test
    void elVigilanteYElConsejoNoPuedenVerComprobantesDePago() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/pagos/1/comprobante", VIGILANTE, CONSEJO, RESIDENTE);
    }

    // ---------- obligaciones, cartera y finanzas ----------

    @Test
    void soloElAdministradorGeneraYAnulaObligaciones() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/obligaciones/periodos/2026-11/generar", excepto(ADMIN));
        prohibidoPara(HttpMethod.PUT, "/api/obligaciones/periodos/2026-11", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/obligaciones", excepto(ADMIN));
        prohibidoPara(HttpMethod.PATCH, "/api/obligaciones/1/anular?motivo=prueba", excepto(ADMIN));
    }

    @Test
    void laCarteraSoloLaVenAdministradorYConsejo() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/cartera", excepto(ADMIN, CONSEJO));
        prohibidoPara(HttpMethod.GET, "/api/recaudo", excepto(ADMIN, CONSEJO));
        prohibidoPara(HttpMethod.GET, "/api/gastos", excepto(ADMIN, CONSEJO));
    }

    @Test
    void elConsejoSoloConsultaYNuncaModificaFinanzas() throws Exception {
        // RN-31: el consejo no registra gastos, intereses ni pagos.
        prohibidoPara(HttpMethod.POST, "/api/gastos", CONSEJO);
        prohibidoPara(HttpMethod.POST, "/api/cartera/intereses-mora", CONSEJO);
        prohibidoPara(HttpMethod.POST, "/api/pagos/presencial", CONSEJO);
        prohibidoPara(HttpMethod.POST, "/api/obligaciones", CONSEJO);
        permitidoPara(HttpMethod.GET, "/api/gastos", CONSEJO);
    }

    // ---------- auditoría y configuración ----------

    @Test
    void laAuditoriaSoloLaVenAdministradorYConsejo() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/auditoria", excepto(ADMIN, CONSEJO));
        prohibidoPara(HttpMethod.GET, "/api/auditoria/exportar", excepto(ADMIN, CONSEJO));
        permitidoPara(HttpMethod.GET, "/api/auditoria", ADMIN, CONSEJO);
    }

    @Test
    void soloElAdministradorGestionaUsuariosYConfiguracion() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/usuarios", excepto(ADMIN));
        prohibidoPara(HttpMethod.PUT, "/api/configuracion", excepto(ADMIN));
        prohibidoPara(HttpMethod.PUT, "/api/notificaciones/config/PAGO_CONFIRMADO", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/inmuebles", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/integrantes", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/vinculaciones", excepto(ADMIN));
    }

    // ---------- portería (RN-13) ----------

    @Test
    void laPorteriaEsSoloDelVigilante() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/porteria/dentro", excepto(VIGILANTE));
        prohibidoPara(HttpMethod.POST, "/api/porteria/ingresos", excepto(VIGILANTE));
        prohibidoPara(HttpMethod.POST, "/api/porteria/ingresos/sin-autorizacion", excepto(VIGILANTE));
        prohibidoPara(HttpMethod.POST, "/api/porteria/vehiculos/ingresos", excepto(VIGILANTE));
        permitidoPara(HttpMethod.GET, "/api/porteria/dentro", VIGILANTE);
    }

    @Test
    void elVigilanteNoModificaVinculacionesObligacionesNiCesiones() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/vinculaciones", VIGILANTE);
        prohibidoPara(HttpMethod.PATCH, "/api/vinculaciones/1/finalizar", VIGILANTE);
        prohibidoPara(HttpMethod.POST, "/api/obligaciones", VIGILANTE);
        prohibidoPara(HttpMethod.POST, "/api/parqueaderos/1/cesiones", VIGILANTE);
        prohibidoPara(HttpMethod.PUT, "/api/parqueaderos/1/titular", VIGILANTE);
    }

    @Test
    void elHistorialDeAccesosEsDelAdministrador() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/accesos/historial", excepto(ADMIN));
        prohibidoPara(HttpMethod.GET, "/api/inmuebles/1/historial", excepto(ADMIN));
    }

    // ---------- visitas y autorizaciones (CU-05 y CU-06) ----------

    @Test
    void soloPropietarioYArrendatarioRegistranVisitasYAutorizaciones() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/visitas", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.POST, "/api/autorizaciones", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.PATCH, "/api/autorizaciones/1/revocar", excepto(PROPIETARIO, ARRENDATARIO));
    }

    // ---------- reservas y notificaciones ----------

    @Test
    void reservarEsDeLosResidentesYGestionarReservasEsDelAdministrador() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/reservas", excepto(PROPIETARIO, ARRENDATARIO, RESIDENTE));
        prohibidoPara(HttpMethod.PATCH, "/api/reservas/1/confirmar", excepto(ADMIN));
        prohibidoPara(HttpMethod.PATCH, "/api/reservas/1/finalizar", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/reservas/1/multa", excepto(ADMIN));
    }

    @Test
    void laBandejaDeAvisosEsDeLosResidentes() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/notificaciones/mias", ADMIN, CONSEJO, VIGILANTE);
        prohibidoPara(HttpMethod.GET, "/api/notificaciones", excepto(ADMIN));
    }

    // ---------- reportes ----------

    @Test
    void elConsejoNoCreaReportesYSoloElAdministradorLosGestiona() throws Exception {
        prohibidoPara(HttpMethod.POST, "/api/reportes", CONSEJO, RESIDENTE, ADMIN);
        prohibidoPara(HttpMethod.PATCH, "/api/reportes/1/estado", excepto(ADMIN));
        prohibidoPara(HttpMethod.PATCH, "/api/reportes/1/cerrar", excepto(ADMIN));
        prohibidoPara(HttpMethod.POST, "/api/reportes/1/seguimiento", excepto(ADMIN));
    }

    // ---------- Mi inmueble (RF-026) ----------

    @Test
    void miInmuebleEsDeLosResidentes() throws Exception {
        prohibidoPara(HttpMethod.GET, "/api/mis-inmuebles", ADMIN, CONSEJO, VIGILANTE);
    }

    // ---------- pantallas ----------

    @Test
    void cadaRolSoloEntraASusPantallas() throws Exception {
        prohibidoPara(HttpMethod.GET, "/inmuebles", excepto(ADMIN));
        prohibidoPara(HttpMethod.GET, "/historial-inmueble", excepto(ADMIN));
        prohibidoPara(HttpMethod.GET, "/pagos", excepto(ADMIN));
        prohibidoPara(HttpMethod.GET, "/cuentas", excepto(ADMIN));
        prohibidoPara(HttpMethod.GET, "/cartera", excepto(ADMIN, CONSEJO));
        prohibidoPara(HttpMethod.GET, "/auditoria", excepto(ADMIN, CONSEJO));
        prohibidoPara(HttpMethod.GET, "/recaudo", excepto(CONSEJO));
        prohibidoPara(HttpMethod.GET, "/porteria", excepto(VIGILANTE));
        prohibidoPara(HttpMethod.GET, "/control-vehicular", excepto(VIGILANTE));
        prohibidoPara(HttpMethod.GET, "/visitas", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.GET, "/mis-obligaciones", excepto(PROPIETARIO, ARRENDATARIO));
        prohibidoPara(HttpMethod.GET, "/mis-inmuebles", ADMIN, CONSEJO, VIGILANTE);
        prohibidoPara(HttpMethod.GET, "/zonas-comunes", ADMIN, CONSEJO, VIGILANTE);
    }
}
