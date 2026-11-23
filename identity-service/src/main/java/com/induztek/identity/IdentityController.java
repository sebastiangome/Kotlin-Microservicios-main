package com.induztek.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.induztek.security.ApiError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IdentityController {
    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @Tag(name = "Autenticación")
    @Operation(summary = "Registrar una cuenta nueva",
            description = "Crea una cuenta con la contraseña cifrada. Si REGISTRATION_REQUIRE_APPROVAL está activo (por defecto), la cuenta queda inactiva hasta que un administrador la apruebe. No se puede pedir el rol ADMIN; si el rol pedido no existe se asigna FIELD_TECHNICIAN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/auth/register\", \"details\": [{\"field\": \"password\", \"message\": \"size must be between 10 and 72\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/auth/register\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Se intentó auto-asignar el rol ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"This role cannot be self-assigned\", \"path\": \"/api/auth/register\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"An account with this email already exists\", \"path\": \"/api/auth/register\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/auth/register\", \"details\": []}"))),
            @ApiResponse(responseCode = "503", description = "roles-service no responde, así que no se pudo validar el rol pedido.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 503, \"error\": \"Service Unavailable\", \"code\": \"SERVICE_UNAVAILABLE\", \"message\": \"Role service is unavailable\", \"path\": \"/api/auth/register\", \"details\": []}")))})
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityService.UserResponse register(@Valid @RequestBody IdentityService.RegisterRequest request) {
        return identity.register(request);
    }

    @Tag(name = "Autenticación")
    @Operation(summary = "Iniciar sesión",
            description = "Valida correo y contraseña y devuelve un token JWT (HS256, 480 minutos por defecto). Tras 5 intentos fallidos seguidos para un mismo correo, ese correo queda bloqueado 15 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión iniciada. Incluye el token JWT.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/auth/login\", \"details\": [{\"field\": \"email\", \"message\": \"must be a well-formed email address\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/auth/login\", \"details\": []}")})),
            @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos. El mensaje es el mismo para ambos casos a propósito.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "UNAUTHORIZED", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 401, \"error\": \"Unauthorized\", \"code\": \"UNAUTHORIZED\", \"message\": \"Invalid email or password\", \"path\": \"/api/auth/login\", \"details\": []}"))),
            @ApiResponse(responseCode = "403", description = "La contraseña es correcta pero la cuenta está pendiente de aprobación o desactivada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"Account is awaiting administrator approval or disabled\", \"path\": \"/api/auth/login\", \"details\": []}"))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos para este correo. Reintentar pasados 15 minutos.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "TOO_MANY_REQUESTS", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 429, \"error\": \"Too Many Requests\", \"code\": \"TOO_MANY_REQUESTS\", \"message\": \"Too many failed attempts. Try again later\", \"path\": \"/api/auth/login\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/auth/login\", \"details\": []}")))})
    @PostMapping("/api/auth/login")
    public IdentityService.LoginResponse login(@Valid @RequestBody IdentityService.LoginRequest request) {
        return identity.login(request);
    }

    @Tag(name = "Recuperación de contraseña")
    @Operation(summary = "Pedir código de recuperación",
            description = "Envía por correo un código de 6 dígitos (vigente 10 minutos). Responde 202 aunque el correo no exista o la cuenta esté inactiva, para no revelar qué cuentas existen.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud aceptada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityController.Message.class))),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/auth/password/recovery\", \"details\": [{\"field\": \"email\", \"message\": \"must be a well-formed email address\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/auth/password/recovery\", \"details\": []}")})),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/auth/password/recovery\", \"details\": []}"))),
            @ApiResponse(responseCode = "502", description = "No se pudo entregar el correo (notifications-service o el SMTP fallaron). El código queda anulado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "BAD_GATEWAY", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 502, \"error\": \"Bad Gateway\", \"code\": \"BAD_GATEWAY\", \"message\": \"Could not deliver the password recovery email\", \"path\": \"/api/auth/password/recovery\", \"details\": []}")))})
    @PostMapping("/api/auth/password/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Message requestRecovery(@Valid @RequestBody IdentityService.RecoveryRequest request) {
        identity.requestPasswordReset(request.email());
        return new Message("If the account is active, a recovery code has been sent.");
    }

    @Tag(name = "Recuperación de contraseña")
    @Operation(summary = "Cambiar la contraseña con el código",
            description = "Cambia la contraseña si el código es correcto. Al terminar se invalidan todos los códigos pendientes de ese correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña actualizada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityController.Message.class))),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado). (3) Código incorrecto, vencido, ya usado o con los 5 intentos agotados.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/auth/password/reset\", \"details\": [{\"field\": \"newPassword\", \"message\": \"size must be between 10 and 72\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/auth/password/reset\", \"details\": []}"), @ExampleObject(name = "BAD_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"BAD_REQUEST\", \"message\": \"Invalid or expired recovery code\", \"path\": \"/api/auth/password/reset\", \"details\": []}")})),
            @ApiResponse(responseCode = "404", description = "La cuenta ya no existe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Account not found\", \"path\": \"/api/auth/password/reset\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/auth/password/reset\", \"details\": []}")))})
    @PostMapping("/api/auth/password/reset")
    public Message resetPassword(@Valid @RequestBody IdentityService.ResetPasswordRequest request) {
        identity.resetPassword(request);
        return new Message("Password updated.");
    }

    @Tag(name = "Recuperación de contraseña")
    @Operation(summary = "Verificar código de recuperación",
            description = "Comprueba que el código sea correcto y siga vigente sin cambiar la contraseña. Cada intento fallido cuenta: al quinto el código se invalida y hay que pedir uno nuevo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Código válido."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado). (3) Código incorrecto, vencido, ya usado o con los 5 intentos agotados.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/auth/password/verify\", \"details\": [{\"field\": \"code\", \"message\": \"must match \\\"\\\\d{6}\\\"\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/auth/password/verify\", \"details\": []}"), @ExampleObject(name = "BAD_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"BAD_REQUEST\", \"message\": \"Invalid or expired recovery code\", \"path\": \"/api/auth/password/verify\", \"details\": []}")})),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/auth/password/verify\", \"details\": []}")))})
    @PostMapping("/api/auth/password/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyRecoveryCode(@Valid @RequestBody IdentityService.VerifyResetCodeRequest request) {
        identity.verifyPasswordResetCode(request.email(), request.code());
    }

    @Tag(name = "Usuarios")
    @Operation(summary = "Ver mi cuenta",
            description = "Devuelve los datos de la cuenta dueña del token.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos de la cuenta.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "La cuenta del token ya no existe (por ejemplo, fue eliminada).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Account not found\", \"path\": \"/api/usuarios/me\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios/me\", \"details\": []}")))})
    @GetMapping("/api/usuarios/me")
    public IdentityService.UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return identity.getCurrentUser(jwt.getSubject());
    }

    @Tag(name = "Usuarios")
    @Operation(summary = "Actualizar mi perfil",
            description = "Cambia nombre, correo, empresa, foto o biografía. Solo se modifican los campos enviados.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/usuarios/me/perfil\", \"details\": [{\"field\": \"name\", \"message\": \"size must be between 0 and 120\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/usuarios/me/perfil\", \"details\": []}")})),
            @ApiResponse(responseCode = "404", description = "La cuenta del token ya no existe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Account not found\", \"path\": \"/api/usuarios/me/perfil\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "El nuevo correo pertenece a otra cuenta.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"An account with this email already exists\", \"path\": \"/api/usuarios/me/perfil\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios/me/perfil\", \"details\": []}")))})
    @PatchMapping("/api/usuarios/me/perfil")
    public IdentityService.UserResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IdentityService.ProfileUpdateRequest request) {
        return identity.updateProfile(jwt.getSubject(), request);
    }

    @Tag(name = "Administración de usuarios")
    @Operation(summary = "Listar usuarios",
            description = "Solo ADMIN. Devuelve todas las cuentas.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de cuentas.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = IdentityService.UserResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/usuarios\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios\", \"details\": []}")))})
    @GetMapping("/api/usuarios")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<IdentityService.UserResponse> users() {
        return identity.listUsers();
    }

    @Tag(name = "Administración de usuarios")
    @Operation(summary = "Activar o desactivar una cuenta",
            description = "Solo ADMIN. Aprueba cuentas nuevas o desactiva una existente. Un token ya emitido sigue siendo válido hasta que expire.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/usuarios/3f2c1a9e/estado\", \"details\": [{\"field\": \"active\", \"message\": \"must not be null\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/usuarios/3f2c1a9e/estado\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/usuarios/3f2c1a9e/estado\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese id.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Account not found\", \"path\": \"/api/usuarios/3f2c1a9e/estado\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios/3f2c1a9e/estado\", \"details\": []}")))})
    @PatchMapping("/api/usuarios/{id}/estado")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public IdentityService.UserResponse setUserState(
            @PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        return identity.setActive(id, request.active());
    }

    @Tag(name = "Administración de usuarios")
    @Operation(summary = "Eliminar una cuenta",
            description = "Solo ADMIN. Eliminación definitiva.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cuenta eliminada."),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/usuarios/3f2c1a9e\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese id.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Account not found\", \"path\": \"/api/usuarios/3f2c1a9e\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios/3f2c1a9e\", \"details\": []}")))})
    @DeleteMapping("/api/usuarios/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        identity.deleteUser(id);
    }

    @Tag(name = "Administración de usuarios")
    @Operation(summary = "Cambiar el rol de una cuenta",
            description = "Solo ADMIN. El rol debe existir en roles-service.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = IdentityService.UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": [{\"field\": \"roleCode\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe la cuenta o el código de rol indicado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Role not found\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": []}"))),
            @ApiResponse(responseCode = "503", description = "roles-service no responde, no se pudo validar el rol.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 503, \"error\": \"Service Unavailable\", \"code\": \"SERVICE_UNAVAILABLE\", \"message\": \"Role service is unavailable\", \"path\": \"/api/usuarios/3f2c1a9e/rol\", \"details\": []}")))})
    @PatchMapping("/api/usuarios/{id}/rol")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public IdentityService.UserResponse setUserRole(
            @PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return identity.setRole(id, request.roleCode());
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record RoleRequest(@jakarta.validation.constraints.NotBlank String roleCode) {
    }

    public record Message(String message) {
    }
}
