package com.induztek.catalog;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final EquipoRepository equipos;
    private final InstrumentoRepository instrumentos;

    public CatalogController(EquipoRepository equipos, InstrumentoRepository instrumentos) {
        this.equipos = equipos;
        this.instrumentos = instrumentos;
    }

    @Tag(name = "Equipos")
    @Operation(summary = "Listar equipos",
            description = "Los roles internos ven todos los equipos y pueden filtrar por cliente. Un CLIENT solo ve los de su empresa; si su token no trae empresa recibe una lista vacía.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de equipos.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CatalogController.EquipoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos\", \"details\": []}")))})
    @GetMapping("/equipos")
    public List<EquipoResponse> listEquipment(
            @RequestParam(required = false) String cliente,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isClient = "CLIENT".equals(jwt.getClaimAsString("role"));
        String scope = isClient ? jwt.getClaimAsString("company") : cliente;
        if (isClient && (scope == null || scope.isBlank())) {
            return List.of();
        }
        return equipos.findAll().stream()
                .filter(equipo -> scope == null || scope.equalsIgnoreCase(equipo.getCliente()))
                .map(EquipoResponse::from)
                .toList();
    }

    @Tag(name = "Equipos")
    @Operation(summary = "Ver un equipo por código",
            description = "Un CLIENT solo puede ver equipos de su empresa; para los demás responde 404 (no se revela que existen).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo encontrado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.EquipoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe el equipo, o pertenece a otra empresa y quien consulta es CLIENT.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Equipment not found\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}")))})
    @GetMapping("/equipos/{codigo}")
    public EquipoResponse getEquipment(@PathVariable String codigo, @AuthenticationPrincipal Jwt jwt) {
        EquipoEntity entity = equipos.findByCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        if ("CLIENT".equals(jwt.getClaimAsString("role"))
                && !java.util.Objects.equals(entity.getCliente(), jwt.getClaimAsString("company"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found");
        }
        return EquipoResponse.from(entity);
    }

    @Tag(name = "Uso interno")
    @Operation(summary = "¿Existe este equipo? (entre servicios)",
            description = "Solo para llamadas entre servicios (lo usa operations-service). El API Gateway lo bloquea y responde 404 desde afuera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indica si el equipo existe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.EquipmentExistsResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos/internal/TR-500KVA-01\", \"details\": []}")))})
    @GetMapping("/equipos/internal/{codigo}")
    public EquipmentExistsResponse findEquipmentInternal(@PathVariable String codigo) {
        return new EquipmentExistsResponse(equipos.findByCodigo(codigo).isPresent());
    }

    @Tag(name = "Equipos")
    @Operation(summary = "Detectar un equipo duplicado",
            description = "Busca un equipo en la misma ubicación con el mismo tag, número de serie o código. Un CLIENT solo compara contra equipos de su empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la comparación.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.DuplicateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Falta el parámetro obligatorio ubicacion.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "MISSING_PARAMETER", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MISSING_PARAMETER\", \"message\": \"Missing required parameter 'ubicacion'\", \"path\": \"/api/equipos/duplicados\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos/duplicados\", \"details\": []}")))})
    @GetMapping("/equipos/duplicados")
    public DuplicateResponse duplicateCheck(
            @RequestParam String ubicacion,
            @RequestParam(required = false, defaultValue = "") String tag,
            @RequestParam(required = false, defaultValue = "") String numeroSerie,
            @RequestParam(required = false, defaultValue = "") String codigo,
            @AuthenticationPrincipal Jwt jwt) {
        boolean isClient = isClient(jwt);
        String company = jwt.getClaimAsString("company");
        return equipos.findAll().stream()
                .filter(equipo -> !isClient || (company != null && company.equalsIgnoreCase(equipo.getCliente())))
                .filter(equipo -> ubicacion.equalsIgnoreCase(equipo.getUbicacion()))
                .filter(equipo -> (!tag.isBlank() && tag.equalsIgnoreCase(equipo.getTagCliente()))
                        || (!numeroSerie.isBlank() && numeroSerie.equalsIgnoreCase(equipo.getNumeroSerie()))
                        || (!codigo.isBlank() && codigo.equalsIgnoreCase(equipo.getCodigo())))
                .findFirst()
                .map(EquipmentDuplicate::from)
                .map(duplicate -> new DuplicateResponse(true, duplicate))
                .orElseGet(() -> new DuplicateResponse(false, null));
    }

    @Tag(name = "Equipos")
    @Operation(summary = "Registrar un equipo",
            description = "Roles ADMIN y FIELD_TECHNICIAN. El código debe ser único.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Equipo creado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.EquipoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/equipos\", \"details\": [{\"field\": \"codigo\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/equipos\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o FIELD_TECHNICIAN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/equipos\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe un equipo con ese código.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Equipment code already exists\", \"path\": \"/api/equipos\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos\", \"details\": []}")))})
    @PostMapping("/equipos")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoResponse createEquipment(@Valid @RequestBody EquipoRequest request) {
        if (equipos.findByCodigo(request.codigo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Equipment code already exists");
        }
        return EquipoResponse.from(equipos.save(request.toEntity(UUID.randomUUID().toString())));
    }

    @Tag(name = "Equipos")
    @Operation(summary = "Modificar un equipo",
            description = "Roles ADMIN y FIELD_TECHNICIAN. Si se cambia el código, el nuevo no puede estar en uso.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Equipo actualizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.EquipoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": [{\"field\": \"ubicacion\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o FIELD_TECHNICIAN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe el equipo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Equipment not found\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "El nuevo código ya lo usa otro equipo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Equipment code already exists\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/equipos/TR-500KVA-01\", \"details\": []}")))})
    @PutMapping("/equipos/{codigo}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN')")
    public EquipoResponse updateEquipment(@PathVariable String codigo, @Valid @RequestBody EquipoRequest request) {
        EquipoEntity entity = equipos.findByCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        if (!codigo.equals(request.codigo()) && equipos.findByCodigo(request.codigo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Equipment code already exists");
        }
        entity.update(request);
        return EquipoResponse.from(equipos.save(entity));
    }

    @Tag(name = "Instrumentos")
    @Operation(summary = "Listar instrumentos de medición",
            description = "No disponible para el rol CLIENT. Con activos=true solo devuelve los activos.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de instrumentos.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = CatalogController.InstrumentoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "403", description = "El rol CLIENT no puede consultar instrumentos.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/instrumentos\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/instrumentos\", \"details\": []}")))})
    @GetMapping("/instrumentos")
    public List<InstrumentoResponse> listInstruments(
            @RequestParam(required = false, defaultValue = "false") boolean activos,
            @AuthenticationPrincipal Jwt jwt) {
        denyClients(jwt);
        return (activos ? instrumentos.findByActivoTrue() : instrumentos.findAll())
                .stream().map(InstrumentoResponse::from).toList();
    }

    @Tag(name = "Instrumentos")
    @Operation(summary = "Ver un instrumento",
            description = "No disponible para el rol CLIENT.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instrumento encontrado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.InstrumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "403", description = "El rol CLIENT no puede consultar instrumentos.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/instrumentos/INS-MEG-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe el instrumento.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Instrument not found\", \"path\": \"/api/instrumentos/INS-MEG-01\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/instrumentos/INS-MEG-01\", \"details\": []}")))})
    @GetMapping("/instrumentos/{id}")
    public InstrumentoResponse getInstrument(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        denyClients(jwt);
        return instrumentos.findById(id).map(InstrumentoResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
    }

    @Tag(name = "Instrumentos")
    @Operation(summary = "Registrar un instrumento",
            description = "Roles ADMIN y TEST_ENGINEER. Si no se envía id se genera uno.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Instrumento creado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.InstrumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/instrumentos\", \"details\": [{\"field\": \"tipo\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/instrumentos\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/instrumentos\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe un instrumento con ese id.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Instrument ID already exists\", \"path\": \"/api/instrumentos\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/instrumentos\", \"details\": []}")))})
    @PostMapping("/instrumentos")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TEST_ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentoResponse createInstrument(@Valid @RequestBody InstrumentoRequest request) {
        String id = request.id() == null || request.id().isBlank() ? UUID.randomUUID().toString() : request.id();
        if (instrumentos.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Instrument ID already exists");
        }
        return InstrumentoResponse.from(instrumentos.save(new InstrumentoEntity(
                id, request.tipo(), request.marca(), request.modelo(), request.numeroSerie(),
                request.vencimientoCalibracion(), request.activo() == null || request.activo())));
    }

    @Tag(name = "Instrumentos")
    @Operation(summary = "Actualizar vencimiento de calibración",
            description = "Roles ADMIN y TEST_ENGINEER.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Calibración actualizada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.InstrumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/instrumentos/INS-MEG-01/calibracion\", \"details\": [{\"field\": \"vencimientoCalibracion\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/instrumentos/INS-MEG-01/calibracion\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/instrumentos/INS-MEG-01/calibracion\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe el instrumento.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Instrument not found\", \"path\": \"/api/instrumentos/INS-MEG-01/calibracion\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/instrumentos/INS-MEG-01/calibracion\", \"details\": []}")))})
    @PutMapping("/instrumentos/{id}/calibracion")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TEST_ENGINEER')")
    public InstrumentoResponse updateCalibration(@PathVariable String id, @Valid @RequestBody CalibrationRequest request) {
        InstrumentoEntity entity = instrumentos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        entity.setVencimientoCalibracion(request.vencimientoCalibracion());
        return InstrumentoResponse.from(instrumentos.save(entity));
    }

    @Tag(name = "Instrumentos")
    @Operation(summary = "Activar o desactivar un instrumento",
            description = "Solo ADMIN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = CatalogController.InstrumentoResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/instrumentos/INS-MEG-01/estado\", \"details\": [{\"field\": \"activo\", \"message\": \"must not be null\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/instrumentos/INS-MEG-01/estado\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/instrumentos/INS-MEG-01/estado\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe el instrumento.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Instrument not found\", \"path\": \"/api/instrumentos/INS-MEG-01/estado\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/instrumentos/INS-MEG-01/estado\", \"details\": []}")))})
    @PutMapping("/instrumentos/{id}/estado")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public InstrumentoResponse setInstrumentActive(@PathVariable String id, @Valid @RequestBody ActiveRequest request) {
        InstrumentoEntity entity = instrumentos.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found"));
        entity.setActivo(request.activo());
        return InstrumentoResponse.from(instrumentos.save(entity));
    }

    private static boolean isClient(Jwt jwt) {
        return "CLIENT".equals(jwt.getClaimAsString("role"));
    }

    private static void denyClients(Jwt jwt) {
        if (isClient(jwt)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clients cannot access instruments");
        }
    }

    public record EquipoRequest(
            @NotBlank String codigo,
            @Size(max = 80) String tagCliente,
            @NotBlank String tipo,
            String cliente,
            @NotBlank String ubicacion,
            String marca,
            String modelo,
            String numeroSerie,
            String potenciaTension,
            String anio,
            String tensiones,
            String tap,
            String conexion,
            @NotBlank String ultimoEstado) {
        EquipoEntity toEntity(String id) {
            return new EquipoEntity(id, codigo, tagCliente, tipo, cliente, ubicacion, marca, modelo,
                    numeroSerie, potenciaTension, anio, tensiones, tap, conexion, ultimoEstado);
        }
    }

    public record EquipoResponse(
            String id, String codigo, String tagCliente, String tipo, String cliente, String ubicacion,
            String marca, String modelo, String numeroSerie, String potenciaTension, String anio,
            String tensiones, String tap, String conexion, String ultimoEstado) {
        static EquipoResponse from(EquipoEntity entity) {
            return new EquipoResponse(entity.getId(), entity.getCodigo(), entity.getTagCliente(), entity.getTipo(),
                    entity.getCliente(), entity.getUbicacion(), entity.getMarca(), entity.getModelo(),
                    entity.getNumeroSerie(), entity.getPotenciaTension(), entity.getAnio(), entity.getTensiones(),
                    entity.getTap(), entity.getConexion(), entity.getUltimoEstado());
        }
    }

    public record EquipmentExistsResponse(boolean exists) {
    }

    public record EquipmentDuplicate(String id, String codigo, String tagCliente, String numeroSerie) {
        static EquipmentDuplicate from(EquipoEntity entity) {
            return new EquipmentDuplicate(entity.getId(), entity.getCodigo(), entity.getTagCliente(), entity.getNumeroSerie());
        }
    }

    public record DuplicateResponse(boolean duplicate, EquipmentDuplicate equipment) {
    }

    public record InstrumentoRequest(
            String id,
            @NotBlank String tipo,
            String marca,
            String modelo,
            String numeroSerie,
            String vencimientoCalibracion,
            Boolean activo) {
    }

    public record InstrumentoResponse(
            String id, String tipo, String marca, String modelo, String numeroSerie,
            String vencimientoCalibracion, boolean activo) {
        static InstrumentoResponse from(InstrumentoEntity entity) {
            return new InstrumentoResponse(entity.getId(), entity.getTipo(), entity.getMarca(), entity.getModelo(),
                    entity.getNumeroSerie(), entity.getVencimientoCalibracion(), entity.isActivo());
        }
    }

    public record CalibrationRequest(@NotBlank String vencimientoCalibracion) {
    }

    public record ActiveRequest(@NotNull Boolean activo) {
    }
}
