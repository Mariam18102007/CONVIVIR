package com.proyecto.plataforma_ph.config;

import com.proyecto.plataforma_ph.controller.InicioController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Agrega a cada página el usuario, su rol y el menú lateral según el rol.
 * disponible = true: el módulo ya está construido.
 * disponible = false: muestra la pantalla "Próximamente".
 */
@ControllerAdvice(assignableTypes = {InicioController.class})
public class ModeloGlobal {

    public record ItemMenu(String titulo, String url, boolean disponible, boolean activo) {}

    @ModelAttribute
    public void datosComunes(Authentication auth, HttpServletRequest request, Model model) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return;
        }

        String a = request.getRequestURI();
        List<ItemMenu> menu = new ArrayList<>();
        menu.add(item("Inicio", "/", true, a));

        if (tieneRol(auth, "ROLE_ADMINISTRADOR")) {
            menu.add(item("Inmuebles", "/inmuebles", true, a));
            menu.add(item("Tipos de inmueble", "/tipos-inmueble", true, a));
            menu.add(item("Integrantes", "/integrantes", true, a));
            menu.add(item("Vinculaciones", "/vinculaciones", true, a));
            menu.add(item("Vehículos", "/vehiculos", true, a));
            menu.add(item("Parqueaderos y cesiones", "/parqueaderos", true, a));
            menu.add(item("Historial de accesos", "/historial-accesos", true, a));
            menu.add(item("Obligaciones", "/obligaciones", true, a));
            menu.add(item("Pagos", "/pagos", true, a));
            menu.add(item("Cartera", "/cartera", true, a));
            menu.add(item("Gastos", "/gastos", true, a));
            menu.add(item("Reservas", "/reservas", true, a));
            menu.add(item("Reportes", "/reportes", true, a));
            menu.add(item("Cuentas", "/cuentas", true, a));
            menu.add(item("Notificaciones", "/notificaciones", false, a));
            menu.add(item("Datos de la copropiedad", "/configuracion", false, a));
            menu.add(item("Auditoría", "/auditoria", true, a));

        } else if (tieneRol(auth, "ROLE_MIEMBRO_CONSEJO")) {
            // RN-31: el consejo solo consulta.
            menu.add(item("Cartera", "/cartera", true, a));
            menu.add(item("Recaudo", "/recaudo", true, a));
            menu.add(item("Gastos", "/gastos", true, a));
            menu.add(item("Auditoría", "/auditoria", true, a));

        } else if (tieneRol(auth, "ROLE_VIGILANTE")) {
            menu.add(item("Portería", "/porteria", true, a));
            menu.add(item("Control vehicular", "/control-vehicular", true, a));
            menu.add(item("Reportes", "/mis-reportes", true, a));

        } else if (tieneRol(auth, "ROLE_RESIDENTE_AUTORIZADO")) {
            menu.add(item("Mi inmueble", "/mis-inmuebles", false, a));
            menu.add(item("Zonas comunes", "/zonas-comunes", true, a));

        } else {
            // Propietario y arrendatario.
            menu.add(item("Mi inmueble", "/mis-inmuebles", false, a));
            menu.add(item("Visitas", "/visitas", true, a));
            menu.add(item("Autorizaciones recurrentes", "/autorizaciones", true, a));
            menu.add(item("Mis vehículos", "/mis-vehiculos", true, a));
            menu.add(item("Obligaciones y pagos", "/mis-obligaciones", true, a));
            menu.add(item("Zonas comunes", "/zonas-comunes", true, a));
            menu.add(item("Reportes", "/mis-reportes", true, a));
        }

        menu.add(item("Mi cuenta", "/cuenta", true, a));

        model.addAttribute("menu", menu);
        model.addAttribute("usuario", auth.getName());
        model.addAttribute("rol", nombreRol(auth));
    }

    private ItemMenu item(String titulo, String url, boolean disponible, String actual) {
        return new ItemMenu(titulo, url, disponible, url.equals(actual));
    }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(rol));
    }

    // ROLE_MIEMBRO_CONSEJO -> "Miembro consejo"
    private String nombreRol(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(x -> x.getAuthority().replace("ROLE_", "").replace('_', ' ').toLowerCase())
                .findFirst()
                .map(r -> Character.toUpperCase(r.charAt(0)) + r.substring(1))
                .orElse("");
    }
}