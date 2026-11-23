package com.induztek.roles;

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
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final RoleRepository roles;

    public RoleController(RoleRepository roles) {
        this.roles = roles;
    }

    @Tag(name = "Roles")
    @Operation(summary = "Listar roles",
            description = "Devuelve los roles del sistema (ADMIN, TEST_ENGINEER, FIELD_TECHNICIAN, CLIENT).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de roles.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RoleController.RoleResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/roles\", \"details\": []}")))})
    @GetMapping
    public List<RoleResponse> list() {
        return roles.findAll().stream().map(RoleResponse::from).toList();
    }

    @Tag(name = "Roles")
    @Operation(summary = "Ver un rol por código",
            description = "Busca un rol por su código.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol encontrado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleController.RoleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe un rol con ese código.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Role not found\", \"path\": \"/api/roles/CLIENT\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/roles/CLIENT\", \"details\": []}")))})
    @GetMapping("/{code}")
    public RoleResponse get(@PathVariable String code) {
        return roles.findByCode(code)
                .map(RoleResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    @Tag(name = "Uso interno")
    @Operation(summary = "Consulta de rol entre servicios",
            description = "Solo para llamadas entre servicios dentro de la red de Docker (lo usa identity-service). El API Gateway lo bloquea y responde 404 a quien lo pida desde afuera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol encontrado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleController.RoleResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe un rol con ese código.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Role not found\", \"path\": \"/api/roles/internal/CLIENT\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/roles/internal/CLIENT\", \"details\": []}")))})
    @GetMapping("/internal/{code}")
    public RoleResponse getInternal(@PathVariable String code) {
        return get(code);
    }

    @Tag(name = "Roles")
    @Operation(summary = "Crear un rol",
            description = "Solo ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rol creado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoleController.RoleResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/roles\", \"details\": [{\"field\": \"code\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/roles\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/roles\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe un rol con ese código.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Role code already exists\", \"path\": \"/api/roles\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/roles\", \"details\": []}")))})
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        if (roles.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        }
        return RoleResponse.from(roles.save(new RoleEntity(
                request.id(), request.code(), request.name(), request.description())));
    }

    public record RoleRequest(
            @NotBlank String id,
            @NotBlank String code,
            @NotBlank String name,
            String description) {
    }

    public record RoleResponse(String id, String code, String name, String description) {
        static RoleResponse from(RoleEntity role) {
            return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription());
        }
    }
}
