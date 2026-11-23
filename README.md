# Backend Induztek

## Repositorios

- Aplicación Android (Kotlin): [Eliassss123/Kotlin](https://github.com/Eliassss123/Kotlin/tree/kotlin)
- Backend de microservicios: [Benjamin-Negrete/Kotlin-Microservicios](https://github.com/Benjamin-Negrete/Kotlin-Microservicios)

Backend Java 21 / Spring Boot 3.5 con servicios independientes, Eureka, un API Gateway, MySQL y Swagger UI centralizada.

## Servicios

- `identity-service`: acceso, usuarios, aceptación de términos, recuperación de contraseña y gestión de fotos de perfil (`fotoPerfil`).
- `roles-service`: catálogo de perfiles y permisos.
- `catalog-service`: equipos e instrumentos.
- `operations-service`: visitas, protocolos, mediciones estructuradas en JSON, pruebas y almacenamiento directo de fotos de evidencia fotográfica.
- `notifications-service`: entrega de correos de recuperación mediante SMTP.
- `service-registry`: registro Eureka.
- `api-gateway`: única entrada HTTP para Android y túneles.

Cada servicio dueño de datos usa su propio esquema MySQL. La app móvil no se conecta a MySQL. Las llamadas internas usan `WebClient` con descubrimiento por Eureka; no se usa `RestTemplate`.

## Ejecución local

1. Copiar `.env.example` a `.env` y reemplazar contraseñas y `JWT_SECRET` por valores aleatorios. Configurar SMTP para que funcionen los correos de recuperación.
2. Desde esta carpeta, ejecutar `docker compose up --build`.
3. La app se expone en `http://localhost:8080`; Eureka queda en `http://localhost:8761`.
4. Swagger: `http://localhost:8080/swagger-ui.html`.

Para Laragon/XAMPP, crear las bases `induztek_identity`, `induztek_roles`, `induztek_catalog` e `induztek_operations`, dar permisos al usuario configurado y establecer `IDENTITY_DATABASE_URL`, `ROLES_DATABASE_URL`, `CATALOG_DATABASE_URL` and `OPERATIONS_DATABASE_URL` en `.env`. Desde contenedores Windows, usa `host.docker.internal` como host de MySQL; no uses `localhost`.

## Documentación de la API y códigos de error

Swagger UI (`http://localhost:8080/swagger-ui.html`) junta la documentación de todos los servicios. Cada endpoint indica su descripción, el rol que necesita y **todas las respuestas posibles con un ejemplo real**, incluidos los errores. Para probar endpoints protegidos: hacer `POST /api/auth/login`, copiar el `token` y pulsar **Authorize** (esquema `bearerAuth`).

Todos los servicios (salvo notifications-service, que es interno) responden los errores con el mismo JSON:

```json
{
  "timestamp": "2026-11-23T21:30:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "Account not found",
  "path": "/api/usuarios/me",
  "details": []
}
```

`details` solo trae datos en errores de validación: una lista de `{ "field", "message" }`. Los errores 500 nunca incluyen trazas ni detalles internos; eso queda solo en el log del servicio.

| HTTP | `code` | Cuándo ocurre |
|------|--------|---------------|
| 400 | `VALIDATION_ERROR` | Un campo del cuerpo no cumple las reglas (vacío, formato, largo). Ver `details`. |
| 400 | `MALFORMED_REQUEST` | El cuerpo no es JSON válido o un tipo de dato no coincide. |
| 400 | `INVALID_PARAMETER` | Un parámetro de la URL tiene un valor que no se puede convertir. |
| 400 | `MISSING_PARAMETER` | Falta un parámetro de consulta obligatorio. |
| 400 | `MISSING_PART` | En una subida multipart falta la parte `file`. |
| 400 | `BAD_REQUEST` | Regla de negocio incumplida (código de recuperación inválido o agotado, archivo vacío, nombre de imagen inválido). |
| 401 | `UNAUTHORIZED` | Login con correo o contraseña incorrectos. |
| 401 | (sin cuerpo) | Token JWT ausente, inválido o vencido: lo responde Spring Security solo con el encabezado `WWW-Authenticate`. |
| 403 | `FORBIDDEN` | El rol del token no puede hacer esa acción, o la cuenta está pendiente de aprobación o desactivada. |
| 404 | `NOT_FOUND` | El recurso no existe, o es de otra empresa y quien consulta es `CLIENT`. |
| 405 | `METHOD_NOT_ALLOWED` | El método HTTP no existe para esa ruta. |
| 406 | `NOT_ACCEPTABLE` | El formato de respuesta pedido no está disponible. |
| 409 | `CONFLICT` | Duplicado: correo, código de rol, código de equipo, id existente, protocolo ya formalizado, segunda acta de una visita. |
| 413 | `PAYLOAD_TOO_LARGE` | La imagen supera los 10 MB. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | El archivo no es JPEG o PNG, o el `Content-Type` de la petición no se acepta. |
| 429 | `TOO_MANY_REQUESTS` | 5 intentos de login fallidos seguidos para ese correo: bloqueo de 15 minutos. |
| 500 | `INTERNAL_ERROR` | Error inesperado. |
| 502 | `BAD_GATEWAY` | Falló el envío del correo de recuperación (notifications-service o SMTP). |
| 503 | `SERVICE_UNAVAILABLE` | Un servicio del que depende la operación no responde (roles-service o catalog-service). |

Los endpoints `/internal/**` son solo para llamadas entre servicios: el API Gateway responde 404 si se piden desde afuera.

## Android

- Emulador Android: `http://10.0.2.2:8080/`.
- Teléfono físico: IP LAN del equipo para la URL de API y `PUBLIC_BASE_URL`, por ejemplo `http://192.168.x.x:8080/`.
- Túnel: configurar la URL HTTPS pública del Gateway tanto en la app como en `PUBLIC_BASE_URL`; no exponer MySQL ni los puertos de los microservicios.

La URL se configura mediante `INDUZTEK_API_BASE_URL` en `gradle.properties` local o en `local.properties`; no guardar una URL de túnel temporal en el código.

## Seguridad y datos

Las claves para cargar cuentas de demostración se configuran externamente mediante `IDENTITY_DEMO_USERS_ADMIN_PASSWORD`, `IDENTITY_DEMO_USERS_TECHNICIAN_PASSWORD`, `IDENTITY_DEMO_USERS_LAMPA_PASSWORD`, `IDENTITY_DEMO_USERS_ESCONDIDA_PASSWORD` y `IDENTITY_DEMO_USERS_VENTANAS_PASSWORD`. Cada cuenta se omite si su clave no está configurada o tiene menos de 10 caracteres.

El registro exige aceptar términos y asigna estado pendiente si `REGISTRATION_REQUIRE_APPROVAL=true`. Solo un administrador bootstrap configurado puede aprobar cuentas. Las contraseñas se guardan con BCrypt, y los tokens son JWT firmados con `JWT_SECRET`.

Las imágenes tienen límite de 10 MB y se almacenan en un volumen Docker persistente gestionado por `identity-service` (fotos de perfil) y `operations-service` (fotos de evidencia de pruebas). SMTP y credenciales de despliegue son configuración externa; nunca deben añadirse al repositorio.
