# Plataforma para la gestión y control de propiedades horizontales

Aplicación web para administrar conjuntos residenciales y edificios sometidos al régimen de propiedad horizontal en Colombia (Ley 675 de 2001).

Proyecto de **Ingeniería de Software II** (Grupo T02), Universidad Simón Bolívar, Barranquilla.

## Idea central

La información no se organiza alrededor de las personas, sino alrededor del **inmueble** (unidad privada o bien común). Los propietarios, arrendatarios y residentes cambian con el tiempo; el inmueble permanece. Por eso las obligaciones, los pagos, los vehículos, los accesos y los reportes se guardan asociados al inmueble y conservan su historial, aunque cambien quienes lo habitan.

Cada persona se relaciona con un inmueble mediante una **vinculación** con tipo y vigencia (propietario residente, propietario no residente, arrendatario o residente autorizado), y de esa vinculación depende lo que puede ver y hacer.

## Módulos

| Módulo | Quién lo usa | Qué permite |
|---|---|---|
| Inmuebles y tipos de inmueble | Administrador | Registrar, modificar y desactivar inmuebles; configurar si su tipo permite cesión, reserva y cuota |
| Historial de inmueble | Administrador | Vinculaciones, obligaciones, pagos, reportes y accesos de una unidad |
| Integrantes y vinculaciones | Administrador | Registrar personas y relacionarlas con inmuebles, con historial |
| Mi inmueble | Propietario, arrendatario y residente autorizado | Consultar sus propias vinculaciones vigentes |
| Cuentas, recuperación de contraseña y Mi cuenta | Todos | Cuentas de acceso, cambio de contraseña y datos de contacto propios |
| Datos de la copropiedad | Administrador | Nombre, NIT, dirección y contacto |
| Auditoría | Administrador y consejo | Registro de operaciones con filtros y exportación a CSV |
| Visitas y autorizaciones recurrentes | Propietario y arrendatario | Anunciar visitas y autorizar accesos periódicos con días y horarios |
| Portería | Vigilante | Buscar autorizaciones, registrar ingresos y salidas, ingreso sin autorización con motivo |
| Historial de accesos | Administrador | Entradas y salidas por inmueble y periodo |
| Vehículos | Administrador, propietario y arrendatario | Registrar vehículos por inmueble; la placa es única entre los activos |
| Parqueaderos y cesiones de uso | Administrador | Titularidad, cesiones temporales y uso vigente |
| Control vehicular | Vigilante | Buscar por placa, ingreso y salida de vehículos, parqueaderos de visitantes |
| Reportes y novedades | Propietario, arrendatario, vigilante y administrador | Reportar con imagen de evidencia, asignar responsable, seguimiento y cierre; categorías |
| Obligaciones | Administrador, propietario y arrendatario | Cuotas por periodo (valor base × coeficiente), cobros individuales, consulta de saldos |
| Pagos | Administrador, propietario y arrendatario | Pago presencial con recibo de caja, pago por pasarela simulada, abonos parciales, comprobantes, anulación |
| Cartera | Administrador y consejo | Saldos por inmueble, filtro por mora, exportación CSV, paz y salvo e intereses de mora (estos dos últimos, solo administrador) |
| Recaudo y gastos | Consejo y administrador | Recaudo por canal y medio (consejo); gastos (consejo consulta, administrador registra y anula) |
| Reservas de zonas comunes | Residentes y administrador | Disponibilidad, solicitud y cancelación; confirmación, rechazo, finalización, multas y tarifas (administrador) |
| Notificaciones | Administrador y residentes | Configurar eventos y destinatarios; bandeja de avisos de cada residente |

## Roles

Cada rol ve solo su menú y solo la información que le corresponde:

- **Administrador:** gestión completa de la copropiedad.
- **Miembro del consejo:** solo consulta: cartera, recaudo, gastos y auditoría.
- **Vigilante:** portería, control vehicular y reportes propios. No ve los nombres de los responsables de los vehículos.
- **Propietario y arrendatario:** solo lo de su propio inmueble. El arrendatario únicamente puede pagar la cuota de administración, no multas ni cuotas extraordinarias.
- **Residente autorizado:** Mi inmueble, zonas comunes y notificaciones.

## Seguridad y protección de datos

- Contraseñas con BCrypt y bloqueo de la cuenta tras varios intentos fallidos.
- Recuperación de contraseña con token temporal de un solo uso (se guarda solo su hash).
- Permisos por rol en cada ruta y validación de la vinculación con el inmueble en el servidor.
- Auditoría de las operaciones relevantes, sin guardar contraseñas, tokens ni datos personales de visitantes.
- Imágenes de evidencia validadas por su contenido real, con nombre generado por el servidor y fuera del repositorio.
- La política de datos sigue la Ley 1581 de 2012 y el principio de minimización.

## Tecnologías

- Java 21
- Spring Boot 4.0.9 (Spring MVC, Spring Security, Spring Data JPA)
- Hibernate 7 y SQLite
- Thymeleaf y JavaScript sin frameworks para las pantallas
- Maven (con el wrapper incluido)

## Cómo ejecutarlo

Requisitos: **JDK 21 o superior**. No hay que instalar Maven ni una base de datos.

```bash
git clone https://github.com/Mariam18102007/plataforma-ph.git
cd plataforma-ph
./mvnw spring-boot:run        # en Windows: .\mvnw.cmd spring-boot:run
```

Abre `http://localhost:8080`.

La base de datos SQLite se crea sola en la carpeta desde la que se ejecuta la aplicación, la primera vez que arranca, junto con las tablas, los tipos de inmueble, las categorías de reporte y un administrador inicial.

### Administrador inicial

Si no existe ningún usuario, se crea el administrador `admin@ph.local` (o el definido en `ADMIN_CORREO`):

- Si defines `ADMIN_PASSWORD`, esa es su contraseña.
- Si no, se genera una contraseña aleatoria que se muestra **una sola vez** en la consola al arrancar. Anótala y cámbiala en **Mi cuenta**.

### Configuración

| Variable | Para qué sirve | Valor por defecto |
|---|---|---|
| `DB_PATH` | Ruta del archivo de la base de datos | `./propiedad_horizontal.db` |
| `UPLOADS_DIR` | Carpeta de las imágenes de evidencia | `./uploads` |
| `ADMIN_CORREO` | Correo del administrador inicial | `admin@ph.local` |
| `ADMIN_PASSWORD` | Contraseña del administrador inicial | aleatoria |

### Con Docker

```bash
docker build -t plataforma-ph .
docker run -p 8080:8080 -v ph-data:/app/data plataforma-ph
```

La base y las imágenes se guardan en `/app/data`; el volumen evita perderlas al recrear el contenedor.

### Pruebas

```bash
./mvnw test
```

## Estructura del proyecto

```
src/main/java/com/proyecto/plataforma_ph/
├── config/        Seguridad, menú por rol y datos iniciales
├── controller/    API REST y páginas
├── model/         Entidades y enumeraciones
├── pasarela/      Pasarela de pagos (hoy simulada, lista para cambiarla por una real)
├── repository/    Acceso a datos
├── security/      Autenticación, bloqueo de cuentas y auditoría
└── service/       Notificaciones y avisos de vencimiento
src/main/resources/
├── static/css/    Estilos
└── templates/     Pantallas
src/test/java/     Pruebas
```

## Pasarela de pagos

La integración funciona como una pasarela real: se inicia el pago, la persona lo aprueba o rechaza en la pasarela y el sistema consulta el resultado. **Un pago solo se confirma cuando la pasarela devuelve un resultado válido.** Hoy la pasarela es una simulación, sin movimiento de dinero real. Cambiarla por un proveedor real (por ejemplo, Wompi en modo de pruebas) solo requiere una clase nueva que implemente la interfaz `PasarelaPago`.

## Notificaciones

Los avisos (vencimientos, pagos, reportes, visitantes y autorizaciones) se entregan en la bandeja de cada residente. Los avisos de cobro van al propietario obligado del pago y los operativos a propietarios residentes y arrendatarios. El envío por correo aún no está implementado; en la recuperación de contraseña el enlace se muestra en la consola del servidor.

## Pendiente

- Envío real de correos (recuperación de contraseña y comprobantes).
- Política de tratamiento de datos y aviso de privacidad en pantalla.
- Endurecimiento para producción: HTTPS, cookies seguras, CSRF en la API y pasarela real.
- Más pruebas automáticas.

## Fuera del alcance de esta versión

Reconocimiento automático de placas, automatización de puertas, asambleas y votaciones, aplicación móvil nativa, contabilidad y tributación completas, reporte a centrales de riesgo e inteligencia artificial.

## Equipo

- Mariam Sayoa Bonfante Ordoñez
- Juan Pablo Varela Patiño
- Shaddai Alejandra Pardo Vega
- Carmelo Andrés Cerro Zuluaga
- Keiner Rojas Díaz
- Sebastián Sierra Hernández
- Esteban Eduardo Gutiérrez Polo

Docente: Eliseo Manuel Castro Jiménez