# CONVIVIR — Plataforma para la gestión y control de propiedades horizontales

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
| Cuentas, recuperación de contraseña y Mi cuenta | Todos | Cuentas de acceso, recuperación por correo, cambio de contraseña y datos de contacto propios |
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
| Política de privacidad | Todos | Pantalla pública con la política de tratamiento de datos (Ley 1581 de 2012) |

## Roles

Cada rol ve solo su menú y solo la información que le corresponde:

- **Administrador:** gestión completa de la copropiedad.
- **Miembro del consejo:** solo consulta: cartera, recaudo, gastos y auditoría.
- **Vigilante:** portería, control vehicular y reportes propios. No ve los nombres de los responsables de los vehículos.
- **Propietario y arrendatario:** solo lo de su propio inmueble. El arrendatario únicamente puede pagar la cuota de administración, no multas ni cuotas extraordinarias.
- **Residente autorizado:** Mi inmueble, zonas comunes y notificaciones.

## Seguridad y protección de datos

- Contraseñas con BCrypt y bloqueo de la cuenta tras varios intentos fallidos.
- Recuperación de contraseña con token temporal de un solo uso (se guarda solo su hash). El enlace se arma con `APP_BASE_URL`, no con el encabezado `Host` de la petición, y el correo se envía de forma asíncrona para no revelar qué cuentas existen.
- Permisos por rol en cada ruta y validación de la vinculación con el inmueble en el servidor.
- Auditoría de las operaciones relevantes, sin guardar contraseñas, tokens ni datos personales de visitantes.
- Imágenes de evidencia validadas por su contenido real, con nombre generado por el servidor y fuera del repositorio.
- La política de datos sigue la Ley 1581 de 2012 y el principio de minimización, y está disponible en pantalla en `/privacidad`.

## Tecnologías

- Java 21
- Spring Boot 4.0.9 (Spring MVC, Spring Security, Spring Data JPA, Spring Mail)
- Hibernate 7 y SQLite
- Thymeleaf y JavaScript sin frameworks para las pantallas
- Maven (con el wrapper incluido)

## Cómo ejecutarlo

Requisitos: **JDK 21 o superior**. No hay que instalar Maven ni una base de datos.

```bash
git clone https://github.com/Mariam18102007/CONVIVIR.git
cd CONVIVIR
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
| `APP_BASE_URL` | URL pública con la que se arman los enlaces de los correos | `http://localhost:8080` |
| `MAIL_FROM` | Remitente de los correos | `no-reply@convivir.local` |
| `MAIL_CONSOLA` | Sin SMTP, escribe el mensaje en el log (poner `false` en producción) | `true` |
| `SPRING_MAIL_HOST` | Servidor SMTP. Si no se define, no se envían correos reales | sin definir |
| `SPRING_MAIL_PORT` | Puerto SMTP (por ejemplo, 587) | sin definir |
| `SPRING_MAIL_USERNAME` | Usuario SMTP | sin definir |
| `SPRING_MAIL_PASSWORD` | Contraseña SMTP | sin definir |

Para un SMTP con autenticación y STARTTLS (por ejemplo, Gmail con contraseña de aplicación) agrega también:

```
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

### Con Docker

```bash
docker build -t convivir-ph .
docker run -d --name convivir-app -p 8080:8080 -v "$(pwd)/docker-data:/app/data" convivir-ph
```

En PowerShell (Windows) el volumen se escribe `-v "${PWD}/docker-data:/app/data"`.

La base y las imágenes se guardan en la carpeta `docker-data` (dentro del contenedor, `/app/data`); al estar fuera del contenedor no se pierden al recrearlo y no se suben al repositorio. Para enviar correos reales agrega al `docker run` las variables `-e APP_BASE_URL=... -e SPRING_MAIL_HOST=... -e SPRING_MAIL_PORT=587 ...`.

Para ver la contraseña aleatoria del administrador la primera vez: `docker logs convivir-app`.

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
└── service/       Notificaciones, correo y avisos de vencimiento
src/main/resources/
├── static/css/    Estilos
└── templates/     Pantallas
src/test/java/     Pruebas
```

## Pasarela de pagos

La integración funciona como una pasarela real: se inicia el pago, la persona lo aprueba o rechaza en la pasarela y el sistema consulta el resultado. **Un pago solo se confirma cuando la pasarela devuelve un resultado válido.** Hoy la pasarela es una simulación, sin movimiento de dinero real. Cambiarla por un proveedor real (por ejemplo, Wompi en modo de pruebas) solo requiere una clase nueva que implemente la interfaz `PasarelaPago`.

## Notificaciones y correo

Los avisos (vencimientos, pagos, reportes, visitantes y autorizaciones) se entregan en la bandeja de cada residente. Los avisos de cobro van al propietario obligado del pago y los operativos a propietarios residentes y arrendatarios.

La recuperación de contraseña envía el enlace por correo con `CorreoService`. Si no hay SMTP configurado, el mensaje (con el enlace) se escribe en el log del servidor, lo que sirve para desarrollo.

## Limitaciones conocidas

- La pasarela de pagos es una simulación.
- Los comprobantes y los avisos de la bandeja no se envían por correo; solo el correo de recuperación de contraseña sale por SMTP.
- Pruebas automáticas: cubren pagos, obligaciones, cartera, reservas, portería, notificaciones, cuenta, historial, permisos por rol y mis inmuebles. Faltan los demás módulos.
- Endurecimiento para producción: la API REST no usa token CSRF, y HTTPS y los respaldos de la base dependen de cómo se despliegue (ver las secciones correspondientes cuando se agreguen).
- No hay función en el sistema para anonimizar o suprimir los datos de un titular; las solicitudes se atienden por la administración, como indica la política de privacidad.

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