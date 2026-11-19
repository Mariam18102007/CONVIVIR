package com.proyecto.plataforma_ph.controller;

import com.proyecto.plataforma_ph.config.ModulosPendientes;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class InicioController {

    private boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(rol));
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String inicio() {
        return "home";
    }

    // ---------- Administrador ----------
    @GetMapping("/inmuebles")
    public String inmuebles() {
        return "inmuebles";
    }

    @GetMapping("/tipos-inmueble")
    public String tiposInmueble() {
        return "tipos-inmueble";
    }

    @GetMapping("/integrantes")
    public String integrantes() {
        return "integrantes";
    }

    @GetMapping("/cuentas")
    public String cuentas() {
        return "cuentas";
    }

    @GetMapping("/vinculaciones")
    public String vinculaciones() {
        return "vinculaciones";
    }

    @GetMapping("/historial-accesos")
    public String historialAccesos() {
        return "historial-accesos";
    }

    @GetMapping("/vehiculos")
    public String vehiculos(Model model) {
        model.addAttribute("admin", true);
        return "vehiculos";
    }

    @GetMapping("/parqueaderos")
    public String parqueaderos() {
        return "parqueaderos";
    }

    // Obligaciones: generación y administración (CU-11)
    @GetMapping("/obligaciones")
    public String obligaciones() {
        return "obligaciones";
    }

    // Pagos: pago presencial, recibos y listado (CU-13)
    @GetMapping("/pagos")
    public String pagos() {
        return "pagos";
    }

    // Reservas de zonas comunes: gestión y tarifas (CU-21)
    @GetMapping("/reservas")
    public String reservas() {
        return "reservas";
    }

    // Reportes y novedades: gestión (CU-16)
    @GetMapping("/reportes")
    public String reportes() {
        return "reportes";
    }

    // Datos de la copropiedad (RF-106)
    @GetMapping("/configuracion")
    public String configuracion() {
        return "configuracion";
    }

    // Notificaciones: configuración de eventos y destinatarios (CU-17).
    @GetMapping("/notificaciones")
    public String notificaciones() {
        return "notificaciones";
    }

    // ---------- Administrador y consejo ----------
    @GetMapping("/auditoria")
    public String auditoria() {
        return "auditoria";
    }

    // Cartera (CU-14): el administrador gestiona, el consejo solo consulta.
    @GetMapping("/cartera")
    public String cartera(Authentication auth, Model model) {
        model.addAttribute("admin", tieneRol(auth, "ROLE_ADMINISTRADOR"));
        return "cartera";
    }

    // Gastos (CU-14): el administrador registra, el consejo solo consulta.
    @GetMapping("/gastos")
    public String gastos(Authentication auth, Model model) {
        model.addAttribute("admin", tieneRol(auth, "ROLE_ADMINISTRADOR"));
        return "gastos";
    }

    // ---------- Solo consejo ----------
    @GetMapping("/recaudo")
    public String recaudo() {
        return "recaudo";
    }

    // ---------- Vigilante ----------
    @GetMapping("/porteria")
    public String porteria() {
        return "porteria";
    }

    @GetMapping("/control-vehicular")
    public String controlVehicular() {
        return "control-vehicular";
    }

    // ---------- Propietario y arrendatario ----------
    @GetMapping("/visitas")
    public String visitas() {
        return "visitas";
    }

    @GetMapping("/autorizaciones")
    public String autorizaciones() {
        return "autorizaciones";
    }

    @GetMapping("/mis-vehiculos")
    public String misVehiculos(Model model) {
        model.addAttribute("admin", false);
        return "vehiculos";
    }

    @GetMapping("/mis-obligaciones")
    public String misObligaciones() {
        return "mis-obligaciones";
    }

    // Página de la pasarela de pagos simulada (hace de "banco").
    @GetMapping("/pasarela-simulada/{referencia}")
    public String pasarelaSimulada(@PathVariable String referencia) {
        return "pasarela-simulada";
    }

    // ---------- Propietario, arrendatario y residente autorizado ----------
    // Reservar zonas comunes (CU-20).
    @GetMapping("/zonas-comunes")
    public String zonasComunes() {
        return "zonas-comunes";
    }

    // Mi inmueble: vinculaciones vigentes propias (RF-026).
    @GetMapping("/mis-inmuebles")
    public String misInmuebles() {
        return "mis-inmuebles";
    }

    // Bandeja de avisos de propietarios, arrendatarios y residentes autorizados.
    @GetMapping("/mis-notificaciones")
    public String misNotificaciones() {
        return "mis-notificaciones";
    }

    // ---------- Propietario, arrendatario y vigilante ----------
    // Crear reportes y consultar su estado (CU-15).
    @GetMapping("/mis-reportes")
    public String misReportes(Authentication auth, Model model) {
        model.addAttribute("vigilante", tieneRol(auth, "ROLE_VIGILANTE"));
        return "mis-reportes";
    }

    // ---------- Todos los roles ----------
    @GetMapping("/cuenta")
    public String cuenta() {
        return "cuenta";
    }

    // Módulos pendientes: muestran la pantalla "Próximamente".
    // Las rutas de arriba tienen prioridad sobre esta.
    @GetMapping("/{ruta}")
    public String proximamente(@PathVariable String ruta, Model model) {
        ModulosPendientes.Modulo modulo = ModulosPendientes.buscar("/" + ruta);
        if (modulo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("modulo", modulo);
        return "proximamente";
    }
}