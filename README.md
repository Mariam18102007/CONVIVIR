# Plataforma para la gestión y control de propiedades horizontales

Aplicación web para administrar conjuntos residenciales y edificios sometidos al régimen de propiedad horizontal en Colombia (Ley 675 de 2001).

Proyecto de **Ingeniería de Software II** (Grupo T02), Universidad Simón Bolívar, Barranquilla.

## Idea central

La información no se organiza alrededor de las personas, sino alrededor del **inmueble** (unidad privada o bien común). Los propietarios, arrendatarios y residentes cambian con el tiempo; el inmueble permanece. Por eso las obligaciones, los pagos, los vehículos, los accesos y los reportes se guardan asociados al inmueble y conservan su historial, aunque cambien quienes lo habitan.

Cada persona se relaciona con un inmueble mediante una **vinculación** con tipo y vigencia (propietario residente, propietario no residente, arrendatario o residente autorizado), y de esa vinculación depende lo que puede ver y hacer.

## Módulos

| Módulo | Quién lo usa | Qué permite |
|---|---|---|
| Inmuebles y tipos de inmueble | Administrador | Registrar, modificar y desactivar inmuebles y administrar sus tipos |
| Integrantes y vinculaciones | Administrador | Registrar personas y relacionarlas con inmuebles, con historial |
| Cuentas y auditoría | Administrador y consejo | Cuentas de usuario, registro de auditoría con filtros y exportación a CSV |
| Visitas y autorizaciones recurrentes | Propietario y arrendatario | Anunciar visitas y autorizar accesos periódicos con días y horarios |
| Portería | Vigilante | Buscar autorizaciones, registrar ingresos y salidas, ingreso sin autorización con motivo |
| Historial de accesos | Administrador | Entradas y salidas por inmueble y periodo |
| Vehículos | Administrador, propietario y arrendatario | Registrar vehículos por inmueble; la placa es única entre los activos |
| Parqueaderos y cesiones de uso | Administrador | Titularidad, cesiones temporales y uso vigente |
| Control vehicular | Vigilante | Buscar por placa, registrar ingreso y salida de vehículos |
| Reportes y novedades | Propietario, arrendatario, vigilante y administrador | Reportar daños con imagen de evidencia y hacerles seguimiento hasta cerrarlos |
| Obligaciones | Administrador, propietario y arrendatario | Generar cuotas por periodo (valor base × coeficiente), cobros individuales y consulta de saldos |
| Pagos *(en pruebas)* | Administrador, propietario y arrendatario | Pago presencial con recibo de caja, pago por pasarela simulada, abonos parciales y comprobantes |

**En desarrollo:** cartera y paz y salvo, reservas de zonas comunes, notificaciones, datos de la copropiedad y la consulta "Mi inmueble".

## Roles

Cada rol ve solo su menú y solo la información que le corresponde:

- **Administrador:** gestión completa de la copropiedad.
- **Miembro del consejo:** solo consulta (auditoría y, próximamente, información financiera).
- **Vigilante:** portería y control vehicular. No ve los nombres de los responsables de los vehículos.
- **Propietario y arrendatario:** solo lo de su propio inmueble. El arrendatario únicamente puede pagar la cuota de administración, no multas ni cuotas extraordinarias.
- **Residente autorizado:** próximamente, para reservas de zonas comunes.

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

La base de datos SQLite se crea sola en la carpeta desde la que se ejecuta la aplicación, la primera vez que arranca, junto con las tablas y un administrador de desarrollo.

### Configuración

| Variable | Para qué sirve | Valor por defecto |
|---|---|---|
| `DB_PATH` | Ruta del archivo de la base de datos | `./propiedad_horizontal.db` |
| `UPLOADS_DIR` | Carpeta de las imágenes de evidencia | `./uploads` |

### Cuenta de desarrollo

Al arrancar con la base vacía se crea el administrador `admin@ph.local`. Su contraseña está definida en `DatosIniciales.java` y **debe cambiarse antes de cualquier uso real**.

## Estructura del proyecto

```
src/main/java/com/proyecto/plataforma_ph/
├── config/        Seguridad, menú por rol y datos iniciales
├── controller/    API REST y páginas
├── model/         Entidades y enumeraciones
├── pasarela/      Pasarela de pagos (hoy simulada, lista para cambiarla por una real)
├── repository/    Acceso a datos
└── security/      Autenticación, bloqueo de cuentas y auditoría
src/main/resources/
├── static/css/    Estilos
└── templates/     Pantallas
```

## Pasarela de pagos

La integración funciona como una pasarela real: se inicia el pago, la persona lo aprueba o rechaza en la pasarela y el sistema consulta el resultado. **Un pago solo se confirma cuando la pasarela devuelve un resultado válido.** Hoy la pasarela es una simulación, sin movimiento de dinero real. Cambiarla por un proveedor real (por ejemplo, Wompi en modo de pruebas) solo requiere una clase nueva que implemente la interfaz `PasarelaPago`.

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