package com.proyecto.plataforma_ph.config;

import java.util.List;
import java.util.Map;

/**
 * Módulos que todavía no están construidos. Cada uno muestra una pantalla
 * "Próximamente" con lo que cubrirá, según el documento de requisitos.
 */
public final class ModulosPendientes {

    public record Modulo(String titulo, String casoUso, String quien,
                         String descripcion, List<String> funciones) {}

    private ModulosPendientes() {}

    public static Modulo buscar(String ruta) {
        return POR_RUTA.get(ruta);
    }

    private static Map.Entry<String, Modulo> e(String ruta, String titulo, String casoUso,
                                                String quien, String descripcion, String... funciones) {
        return Map.entry(ruta, new Modulo(titulo, casoUso, quien, descripcion, List.of(funciones)));
    }

    private static final Map<String, Modulo> POR_RUTA = Map.ofEntries(

        // ---------- Administrador ----------
        e("/vehiculos", "Vehículos", "CU-08", "Administrador",
            "Registrar y administrar los vehículos asociados a cada inmueble.",
            "RF-045 Registrar un vehículo con placa, tipo, marca, color e inmueble",
            "RF-047 Indicar el propietario o arrendatario responsable",
            "RF-048 Consultar los vehículos de un inmueble",
            "RF-049 Modificar los datos de un vehículo",
            "RF-050 Dar de baja un vehículo indicando el motivo",
            "RF-055 Consultar el historial de ingresos y salidas por periodo"),

        e("/parqueaderos", "Parqueaderos y cesiones", "CU-09", "Administrador",
            "Registrar parqueaderos, asignarlos a un inmueble titular y controlar las cesiones de uso.",
            "RF-056 Registrar un parqueadero con naturaleza y tipo de inmueble",
            "RF-057 Asignar el parqueadero a un inmueble titular",
            "RF-058 Listar parqueaderos con su titular y su uso vigente",
            "RF-059 Modificar los datos permitidos de un parqueadero",
            "RF-060 Registrar una cesión de uso entre inmuebles",
            "RF-061 Finalizar una cesión de uso vigente",
            "RF-062 Consultar las cesiones vigentes y finalizadas"),

        e("/obligaciones", "Obligaciones", "CU-11", "Administrador",
            "Generar y administrar las cuotas y demás obligaciones de cada inmueble.",
            "RF-065 Configurar el valor base de la cuota de un periodo",
            "RF-066 Generar las obligaciones del periodo para los inmuebles que generan cuota",
            "RF-067 Registrar una obligación individual (cuota extraordinaria o multa)",
            "RF-068 Consultar las obligaciones de un periodo",
            "RF-069 Anular una obligación indicando el motivo"),

        e("/pagos", "Pagos", "CU-13", "Administrador",
            "Registrar los pagos presenciales y consultar el recaudo.",
            "RF-079 Registrar un pago presencial (efectivo, transferencia o cheque)",
            "RF-080 Generar el recibo de caja",
            "RF-081 Anular un pago registrado por error indicando el motivo",
            "RF-082 Consultar los pagos de un periodo, diferenciando el canal"),

        e("/cartera", "Cartera", "CU-14", "Administrador y miembro del consejo",
            "Ver qué inmuebles deben y desde hace cuánto. El consejo la consulta en modo lectura.",
            "RF-083 Listado de inmuebles con saldo pendiente",
            "RF-084 Detalle de la deuda por periodo y concepto",
            "RF-085 Filtrar la cartera por días de mora",
            "RF-086 Exportar el informe de cartera (administrador)",
            "RF-087 Consultar el informe de cartera por inmueble (consejo)",
            "RF-120 Expedir el paz y salvo de un inmueble (administrador)"),

        e("/reservas", "Reservas", "CU-21", "Administrador",
            "Gestionar las solicitudes de reserva de zonas comunes que hacen los residentes.",
            "RF-119 Confirmar una reserva que cumpla la disponibilidad",
            "RF-121 Finalizar una reserva confirmada",
            "RF-122 Registrar la multa por incumplimiento e imputarla al inmueble"),

        e("/reportes", "Reportes y novedades", "CU-16", "Administrador",
            "Dar seguimiento a los daños y novedades que reportan los residentes y el vigilante.",
            "RF-094 Consultar reportes filtrando por estado, categoría e inmueble",
            "RF-095 Ver el detalle, la evidencia y el historial de seguimiento",
            "RF-096 Asignar un responsable",
            "RF-097 Cambiar el estado",
            "RF-098 Registrar una observación de seguimiento",
            "RF-099 Cerrar el reporte con la solución aplicada",
            "RF-100, RF-110 y RF-111 Crear, modificar y desactivar categorías"),

        e("/notificaciones", "Notificaciones", "CU-17", "Administrador",
            "Configurar a quién le llega cada aviso. Aquí se reemplazará el enlace de recuperación que hoy sale por consola.",
            "RF-107 Definir el destinatario de cada evento",
            "RF-107 Habilitar o deshabilitar cada evento: vencimientos, pagos, reportes, visitantes y autorizaciones"),

        e("/configuracion", "Datos de la copropiedad", "CU-18", "Administrador",
            "Datos generales de la propiedad horizontal.",
            "RF-106 Configurar nombre, NIT, dirección y datos de contacto"),

        // ---------- Miembro del consejo ----------
        e("/recaudo", "Recaudo", "CU-14", "Miembro del consejo",
            "Consultar cuánto se ha recaudado, en modo lectura.",
            "RF-088 Total recaudado en un periodo, diferenciado por canal"),

        e("/gastos", "Gastos", "CU-14", "Miembro del consejo",
            "Consultar los gastos de la copropiedad, en modo lectura.",
            "RF-089 Gastos registrados en un periodo"),

        // ---------- Vigilante ----------
        e("/control-vehicular", "Control vehicular", "CU-10", "Vigilante",
            "Control de vehículos y parqueaderos en la portería.",
            "RF-051 Consultar un vehículo por placa y ver su inmueble",
            "RF-063 Ver qué inmueble tiene el uso vigente de un parqueadero",
            "RF-052 Registrar el ingreso de un vehículo y el parqueadero que ocupa",
            "RF-053 Registrar la salida de un vehículo",
            "RF-054 Ver los vehículos que están dentro del conjunto",
            "RF-064 Ver cuántos parqueaderos de visitantes hay disponibles"),

        // ---------- Propietario, arrendatario y residente autorizado ----------
        e("/mis-inmuebles", "Mi inmueble", "RF-026", "Propietario, arrendatario y residente autorizado",
            "Consultar tus vinculaciones vigentes y la información permitida por tu vínculo.",
            "RF-026 Consultar tus propias vinculaciones vigentes",
            "RN-07 Solo ves la información de los inmuebles a los que estás vinculado"),

        e("/mis-vehiculos", "Mis vehículos", "CU-08", "Propietario residente y arrendatario",
            "Registrar y consultar los vehículos de tu inmueble.",
            "RF-046 Registrar un vehículo con placa, tipo, marca y color",
            "RF-047 Indicar el responsable del vehículo entre los vinculados",
            "RF-048 Consultar los vehículos de tu inmueble"),

        e("/mis-obligaciones", "Obligaciones y pagos", "CU-12", "Propietario y arrendatario",
            "Consultar lo que se debe y pagarlo en línea. El arrendatario solo puede pagar expensas ordinarias (RN-22).",
            "RF-070 y RF-071 Ver la obligación vigente y el saldo pendiente",
            "RF-072 Ver la obligación del inmueble que habitas (arrendatario)",
            "RF-073 Ver las obligaciones de todos tus inmuebles (propietario no residente)",
            "RF-074 y RF-075 Seleccionar obligaciones y pagar por la pasarela",
            "RF-123 Abonar un valor parcial",
            "RF-076 y RF-077 Ver el resultado y descargar el comprobante",
            "RF-078 Consultar el historial de pagos por periodo"),

        e("/mis-reportes", "Reportes", "CU-15", "Propietario, arrendatario y vigilante",
            "Reportar daños y novedades, y seguirlos hasta que se cierren.",
            "RF-090 Crear un reporte con categoría y descripción",
            "RF-091 Adjuntar una imagen como evidencia",
            "RF-092 Consultar el estado de tus reportes",
            "RF-093 Consultar el historial de reportes de tu inmueble"),

        e("/zonas-comunes", "Zonas comunes", "CU-20", "Propietario residente, arrendatario y residente autorizado",
            "Reservar el salón social, la zona BBQ y otras zonas. Solo con vínculo residente vigente (RN-43).",
            "RF-115 Consultar la disponibilidad de una zona para un periodo",
            "RF-116 Solicitar una reserva indicando inmueble, periodo y finalidad",
            "RF-117 Consultar tus reservas y su estado",
            "RF-118 Cancelar una reserva propia indicando el motivo")
    );
}