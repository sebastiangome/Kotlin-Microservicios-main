package com.induztek.operations;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
public class OperationsController {
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<List<Map<String, Object>>> JSON_OBJECT_LIST = new TypeReference<>() {};
    private final VisitRepository visits;
    private final ProtocolRepository protocols;
    private final TestRepository tests;
    private final ActaRepository actas;
    private final CatalogClient catalog;
    private final ObjectMapper mapper;

    public OperationsController(
            VisitRepository visits, ProtocolRepository protocols, TestRepository tests,
            ActaRepository actas, CatalogClient catalog, ObjectMapper mapper) {
        this.visits = visits;
        this.protocols = protocols;
        this.tests = tests;
        this.actas = actas;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @Tag(name = "Visitas")
    @Operation(summary = "Listar visitas",
            description = "Un CLIENT solo ve las visitas de su empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de visitas.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OperationsController.VisitResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/visitas\", \"details\": []}")))})
    @GetMapping("/api/visitas")
    public List<VisitResponse> listVisits(@AuthenticationPrincipal Jwt jwt) {
        List<VisitEntity> result = isClient(jwt)
                ? visits.findByClienteIgnoreCase(jwt.getClaimAsString("company"))
                : visits.findAll();
        return result.stream().map(VisitResponse::from).toList();
    }

    @Tag(name = "Visitas")
    @Operation(summary = "Ver una visita",
            description = "Un CLIENT recibe 404 si la visita es de otra empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visita encontrada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.VisitResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe la visita (o es de otra empresa y quien consulta es CLIENT).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Visit not found\", \"path\": \"/api/visitas/V-001\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/visitas/V-001\", \"details\": []}")))})
    @GetMapping("/api/visitas/{id}")
    public VisitResponse getVisit(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        VisitEntity visit = visits.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        ensureVisitAccess(visit, jwt);
        return VisitResponse.from(visit);
    }

    @Tag(name = "Visitas")
    @Operation(summary = "Crear una visita",
            description = "Solo ADMIN o FIELD_TECHNICIAN.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Visita creada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.VisitResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/visitas\", \"details\": [{\"field\": \"ordenTrabajo\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/visitas\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o FIELD_TECHNICIAN.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/visitas\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe una visita con ese id; no se sobrescribe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Visit id already exists\", \"path\": \"/api/visitas\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/visitas\", \"details\": []}")))})
    @PostMapping("/api/visitas")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse createVisit(@Valid @RequestBody VisitRequest request) {
        ensureIdIsFree(request.id() != null && visits.existsById(request.id()), "Visit");
        return VisitResponse.from(visits.save(new VisitEntity(request)));
    }

    @Tag(name = "Visitas")
    @Operation(summary = "Cerrar una visita",
            description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visita cerrada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.VisitResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/visitas/V-001/cerrar\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe la visita.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Visit not found\", \"path\": \"/api/visitas/V-001/cerrar\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/visitas/V-001/cerrar\", \"details\": []}")))})
    @PatchMapping("/api/visitas/{id}/cerrar")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN','ROLE_TEST_ENGINEER')")
    public VisitResponse closeVisit(@PathVariable String id) {
        VisitEntity visit = visits.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found"));
        visit.close();
        return VisitResponse.from(visits.save(visit));
    }

    @Tag(name = "Protocolos")
    @Operation(summary = "Listar protocolos",
            description = "Filtros opcionales visitaId y equipoCodigo. Un CLIENT solo ve protocolos de visitas de su empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de protocolos.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OperationsController.ProtocolResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/protocolos\", \"details\": []}")))})
    @GetMapping("/api/protocolos")
    public List<ProtocolResponse> listProtocols(
            @RequestParam(required = false) String visitaId,
            @RequestParam(required = false) String equipoCodigo,
            @AuthenticationPrincipal Jwt jwt) {
        List<ProtocolEntity> result;
        if (isClient(jwt)) {
            List<String> visitIds = visits.findByClienteIgnoreCase(jwt.getClaimAsString("company")).stream()
                    .map(VisitEntity::getId).toList();
            result = visitIds.isEmpty() ? List.of() : protocols.findByVisitaIdIn(visitIds);
            if (visitaId != null) {
                result = result.stream().filter(protocol -> visitaId.equals(protocol.getVisitaId())).toList();
            }
            if (equipoCodigo != null) {
                result = result.stream().filter(protocol -> equipoCodigo.equals(protocol.getEquipoCodigo())).toList();
            }
        } else {
            result = visitaId != null
                    ? protocols.findByVisitaId(visitaId)
                    : equipoCodigo != null ? protocols.findByEquipoCodigo(equipoCodigo) : protocols.findAll();
        }
        return result.stream().map(this::protocolResponse).toList();
    }

    @Tag(name = "Protocolos")
    @Operation(summary = "Ver un protocolo",
            description = "Un CLIENT recibe 404 si el protocolo es de otra empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Protocolo encontrado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.ProtocolResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe el protocolo (o es de otra empresa y quien consulta es CLIENT).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Protocol not found\", \"path\": \"/api/protocolos/P-001\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/protocolos/P-001\", \"details\": []}")))})
    @GetMapping("/api/protocolos/{id}")
    public ProtocolResponse getProtocol(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        ProtocolEntity protocol = protocols.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocol not found"));
        ensureProtocolAccess(protocol, jwt);
        return protocolResponse(protocol);
    }

    @Tag(name = "Protocolos")
    @Operation(summary = "Crear un protocolo",
            description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER. El protocolo siempre nace REGISTRADO_TERRENO: estado, formalizadoPor y fechaFormalizacion enviados por el cliente se ignoran. El equipo debe existir en el catálogo y la visita en operaciones.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Protocolo creado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.ProtocolResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/protocolos\", \"details\": [{\"field\": \"equipoCodigo\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/protocolos\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/protocolos\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "El equipo no existe en el catálogo, o la visita indicada no existe (Visit not found).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Equipment not found: EQ-X\", \"path\": \"/api/protocolos\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya hay un protocolo para ese equipo en esa visita, el id o el código de protocolo ya existen.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"A protocol already exists for this equipment and visit\", \"path\": \"/api/protocolos\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/protocolos\", \"details\": []}"))),
            @ApiResponse(responseCode = "503", description = "catalog-service no responde, no se pudo validar el equipo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 503, \"error\": \"Service Unavailable\", \"code\": \"SERVICE_UNAVAILABLE\", \"message\": \"Catalog service is unavailable\", \"path\": \"/api/protocolos\", \"details\": []}")))})
    @PostMapping("/api/protocolos")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN','ROLE_TEST_ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProtocolResponse createProtocol(@Valid @RequestBody ProtocolRequest request) {
        catalog.requireEquipment(request.equipoCodigo());
        if (!visits.existsById(request.visitaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found");
        }
        ensureIdIsFree(request.id() != null && protocols.existsById(request.id()), "Protocol");
        if (protocols.existsByVisitaIdAndEquipoCodigo(request.visitaId(), request.equipoCodigo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A protocol already exists for this equipment and visit");
        }
        if (protocols.existsByCodigoProtocolo(request.codigoProtocolo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Protocol code already in use");
        }
        // Estado y formalización nunca vienen del cliente: solo el endpoint /formalizar los modifica.
        ProtocolRequest safe = new ProtocolRequest(
                request.id(), request.codigoProtocolo(), request.visitaId(), request.ordenTrabajo(),
                request.equipoCodigo(), request.tipoEquipo(), request.fechaHora(), null,
                request.resultadoGlobal(), request.tecnico(), request.tecnicoCreador(),
                request.descripcionMantenimiento(), request.instrumento(), request.calibracionVigente(),
                request.temperaturaAmbiente(), request.humedad(), request.actaEntregadaCliente(),
                null, null, request.syncStatus(), request.observaciones(),
                request.pruebas(), request.mediciones(), request.fotos());
        return protocolResponse(protocols.save(new ProtocolEntity(safe, mapper)));
    }

    @Tag(name = "Protocolos")
    @Operation(summary = "Formalizar un protocolo",
            description = "Solo ADMIN o TEST_ENGINEER. Deja el protocolo en FORMALIZADO con el usuario y la fecha. No se puede formalizar dos veces.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Protocolo formalizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.ProtocolResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": [{\"field\": \"usuario\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe el protocolo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Protocol not found\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "El protocolo ya estaba formalizado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Protocol is already formalized\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/protocolos/P-002/formalizar\", \"details\": []}")))})
    @PostMapping("/api/protocolos/{id}/formalizar")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_TEST_ENGINEER')")
    public ProtocolResponse formalizeProtocol(@PathVariable String id, @Valid @RequestBody FormalizeRequest request) {
        ProtocolEntity protocol = protocols.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocol not found"));
        if ("FORMALIZADO".equals(protocol.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Protocol is already formalized");
        }
        protocol.formalize(request.usuario(), LocalDateTime.now().format(LOCAL_DATE_TIME));
        return protocolResponse(protocols.save(protocol));
    }

    @Tag(name = "Pruebas")
    @Operation(summary = "Listar pruebas",
            description = "Filtros opcionales protocoloId y equipoCodigo (también disponible como /api/equipos/{codigo}/pruebas). Un CLIENT solo ve pruebas de su empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pruebas.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OperationsController.TestResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/pruebas\", \"details\": []}")))})
    @GetMapping({"/api/equipos/{codigo}/pruebas", "/api/pruebas"})
    public List<TestResponse> listTests(
            @PathVariable(required = false) String codigo,
            @RequestParam(required = false) String protocoloId,
            @RequestParam(required = false) String equipoCodigo,
            @AuthenticationPrincipal Jwt jwt) {
        String code = codigo != null ? codigo : equipoCodigo;
        List<TestEntity> result = protocoloId != null
                ? tests.findByProtocoloId(protocoloId)
                : code != null ? tests.findByEquipoCodigo(code) : tests.findAll();
        if (isClient(jwt)) {
            result = result.stream().filter(test -> canAccessProtocol(test.getProtocoloId(), jwt)).toList();
        }
        return result.stream().map(this::testResponse).toList();
    }

    @Tag(name = "Pruebas")
    @Operation(summary = "Ver una prueba",
            description = "Un CLIENT recibe 404 si la prueba es de otra empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prueba encontrada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.TestResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe la prueba (o es de otra empresa y quien consulta es CLIENT).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Test not found\", \"path\": \"/api/pruebas/T-001\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/pruebas/T-001\", \"details\": []}")))})
    @GetMapping("/api/pruebas/{id}")
    public TestResponse getTest(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        TestEntity test = tests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found"));
        if (isClient(jwt) && !canAccessProtocol(test.getProtocoloId(), jwt)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found");
        }
        return testResponse(test);
    }

    @Tag(name = "Pruebas")
    @Operation(summary = "Registrar una prueba",
            description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER. Si se indica protocoloId, el protocolo debe existir. El equipo debe existir en el catálogo.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Prueba creada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.TestResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/pruebas\", \"details\": [{\"field\": \"equipoCodigo\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/pruebas\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/pruebas\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "El equipo no existe en el catálogo o el protocolo indicado no existe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Protocol not found\", \"path\": \"/api/pruebas\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "Ya existe una prueba con ese id; no se sobrescribe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"Test id already exists\", \"path\": \"/api/pruebas\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/pruebas\", \"details\": []}"))),
            @ApiResponse(responseCode = "503", description = "catalog-service no responde, no se pudo validar el equipo.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 503, \"error\": \"Service Unavailable\", \"code\": \"SERVICE_UNAVAILABLE\", \"message\": \"Catalog service is unavailable\", \"path\": \"/api/pruebas\", \"details\": []}")))})
    @PostMapping("/api/pruebas")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN','ROLE_TEST_ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TestResponse createTest(@Valid @RequestBody TestRequest request) {
        catalog.requireEquipment(request.equipoCodigo());
        if (request.protocoloId() != null && !request.protocoloId().isBlank()
                && !protocols.existsById(request.protocoloId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocol not found");
        }
        ensureIdIsFree(request.id() != null && tests.existsById(request.id()), "Test");
        return testResponse(tests.save(new TestEntity(request, mapper)));
    }

    @Tag(name = "Actas")
    @Operation(summary = "Listar actas",
            description = "Filtro opcional visitaId. Un CLIENT solo ve actas de su empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de actas.",
                        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = OperationsController.ActaResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/actas\", \"details\": []}")))})
    @GetMapping("/api/actas")
    public List<ActaResponse> listActs(
            @RequestParam(required = false) String visitaId,
            @AuthenticationPrincipal Jwt jwt) {
        if (visitaId != null) {
            List<ActaEntity> result = actas.findByVisitaId(visitaId);
            if (isClient(jwt)) {
                result = result.stream().filter(act -> sameCompany(act.getCliente(), jwt)).toList();
            }
            return result
                    .stream().map(ActaResponse::from).toList();
        }
        return actas.findAll().stream()
                .filter(act -> !isClient(jwt) || sameCompany(act.getCliente(), jwt))
                .map(ActaResponse::from).toList();
    }

    @Tag(name = "Actas")
    @Operation(summary = "Ver un acta",
            description = "Un CLIENT recibe 404 si el acta es de otra empresa.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Acta encontrada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.ActaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "404", description = "No existe el acta (o es de otra empresa y quien consulta es CLIENT).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Act not found\", \"path\": \"/api/actas/ACTA-001\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/actas/ACTA-001\", \"details\": []}")))})
    @GetMapping("/api/actas/{id}")
    public ActaResponse getAct(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        ActaEntity act = actas.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Act not found"));
        if (isClient(jwt) && !sameCompany(act.getCliente(), jwt)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Act not found");
        }
        return ActaResponse.from(act);
    }

    @Tag(name = "Actas")
    @Operation(summary = "Emitir un acta",
            description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER. La visita debe existir y solo puede tener un acta.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Acta creada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = OperationsController.ActaResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "Puede ocurrir por varios motivos: (1) Datos inválidos: campo obligatorio vacío, formato incorrecto o fuera de rango. El arreglo details indica qué campo falló. (2) Cuerpo JSON mal formado o ilegible (por ejemplo, una coma sobrante o un tipo de dato equivocado).",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = {@ExampleObject(name = "VALIDATION_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"VALIDATION_ERROR\", \"message\": \"Request validation failed\", \"path\": \"/api/actas\", \"details\": [{\"field\": \"visitaId\", \"message\": \"must not be blank\"}]}"), @ExampleObject(name = "MALFORMED_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"MALFORMED_REQUEST\", \"message\": \"Malformed or unreadable request body\", \"path\": \"/api/actas\", \"details\": []}")})),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN, FIELD_TECHNICIAN o TEST_ENGINEER.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "FORBIDDEN", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 403, \"error\": \"Forbidden\", \"code\": \"FORBIDDEN\", \"message\": \"You do not have permission to perform this action\", \"path\": \"/api/actas\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe la visita indicada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Visit not found\", \"path\": \"/api/actas\", \"details\": []}"))),
            @ApiResponse(responseCode = "409", description = "La visita ya tiene acta, o el id del acta ya existe.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "CONFLICT", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 409, \"error\": \"Conflict\", \"code\": \"CONFLICT\", \"message\": \"An act already exists for this visit\", \"path\": \"/api/actas\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/actas\", \"details\": []}")))})
    @PostMapping("/api/actas")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_FIELD_TECHNICIAN','ROLE_TEST_ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ActaResponse createAct(@Valid @RequestBody ActaRequest request) {
        if (!visits.existsById(request.visitaId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found");
        }
        ensureIdIsFree(request.id() != null && actas.existsById(request.id()), "Act");
        if (actas.existsByVisitaId(request.visitaId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An act already exists for this visit");
        }
        return ActaResponse.from(actas.save(new ActaEntity(request)));
    }

    private ProtocolResponse protocolResponse(ProtocolEntity entity) {
        return new ProtocolResponse(
                entity.getId(), entity.getCodigoProtocolo(), entity.getVisitaId(), entity.getOrdenTrabajo(),
                entity.getEquipoCodigo(), entity.getTipoEquipo(), entity.getFechaHora(), entity.getEstado(),
                entity.getResultadoGlobal(), entity.getTecnico(), entity.getTecnicoCreador(),
                entity.getDescripcionMantenimiento(), entity.getInstrumento(), entity.isCalibracionVigente(),
                entity.getTemperaturaAmbiente(), entity.getHumedad(), entity.isActaEntregadaCliente(),
                entity.getFormalizadoPor(), entity.getFechaFormalizacion(), entity.getSyncStatus(),
                entity.getObservaciones(), parseJsonList(entity.getPruebasJson()),
                parseJsonList(entity.getMedicionesJson()), parseStringList(entity.getFotosJson()));
    }

    private void ensureIdIsFree(boolean exists, String what) {
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, what + " id already exists");
        }
    }

    private boolean isClient(Jwt jwt) {
        return "CLIENT".equals(jwt.getClaimAsString("role"));
    }

    private boolean sameCompany(String client, Jwt jwt) {
        String company = jwt.getClaimAsString("company");
        return company != null && client != null && company.equalsIgnoreCase(client);
    }

    private void ensureVisitAccess(VisitEntity visit, Jwt jwt) {
        if (isClient(jwt) && !sameCompany(visit.getCliente(), jwt)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit not found");
        }
    }

    private void ensureProtocolAccess(ProtocolEntity protocol, Jwt jwt) {
        if (isClient(jwt)) {
            VisitEntity visit = visits.findById(protocol.getVisitaId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocol not found"));
            ensureVisitAccess(visit, jwt);
        }
    }

    private boolean canAccessProtocol(String protocolId, Jwt jwt) {
        if (protocolId == null || protocolId.isBlank()) {
            return false;
        }
        return protocols.findById(protocolId).map(protocol -> {
            try {
                ensureProtocolAccess(protocol, jwt);
                return true;
            } catch (ResponseStatusException exception) {
                return false;
            }
        }).orElse(false);
    }

    private TestResponse testResponse(TestEntity entity) {
        return new TestResponse(
                entity.getId(), entity.getProtocoloId(), entity.getOrdenTrabajo(), entity.getEquipoCodigo(),
                entity.getTipoPrueba(), entity.getUbicacion(), entity.getFechaHora(), entity.getEstadoPrueba(),
                entity.getMotivoNoRealizada(), entity.getResultado(), entity.getInstrumentoId(),
                entity.getInstrumento(), entity.getInstrumentoSerie(), entity.getInstrumentoCalibracion(),
                entity.isCalibracionVigente(), entity.getTemperaturaAmbiente(), entity.getHumedad(),
                parseJsonList(entity.getMedicionesJson()), entity.getObservaciones(),
                entity.getVariacionHistoricaPorcentaje(), entity.isSuperaVariacion25());
    }

    private List<Map<String, Object>> parseJsonList(String value) {
        try {
            return mapper.readValue(value, JSON_OBJECT_LIST);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored operation data is invalid", exception);
        }
    }

    private List<String> parseStringList(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored image URLs are invalid", exception);
        }
    }

    public record VisitRequest(
            String id, @NotBlank String ordenTrabajo, String fecha, String horaInicio,
            String cliente, String instalacion, String modalidad, String ingenieroACargo,
            String tecnicos, String temperaturaAmbiente, String humedad, String syncStatus) {
    }

    public record VisitResponse(
            String id, String ordenTrabajo, String fecha, String horaInicio, String cliente,
            String instalacion, String modalidad, String ingenieroACargo, String tecnicos,
            String temperaturaAmbiente, String humedad, boolean cerrada, String syncStatus) {
        static VisitResponse from(VisitEntity entity) {
            return new VisitResponse(entity.getId(), entity.getOrdenTrabajo(), entity.getFecha(), entity.getHoraInicio(),
                    entity.getCliente(), entity.getInstalacion(), entity.getModalidad(), entity.getIngenieroACargo(),
                    entity.getTecnicos(), entity.getTemperaturaAmbiente(), entity.getHumedad(),
                    entity.isCerrada(), entity.getSyncStatus());
        }
    }

    public record Measurement(String campo, String valorNumerico, String unidad) {
    }

    public record ProtocolRequest(
            String id, @NotBlank String codigoProtocolo, @NotBlank String visitaId,
            String ordenTrabajo, @NotBlank String equipoCodigo, String tipoEquipo, String fechaHora,
            String estado, String resultadoGlobal, String tecnico, String tecnicoCreador,
            String descripcionMantenimiento, String instrumento, boolean calibracionVigente,
            String temperaturaAmbiente, String humedad, boolean actaEntregadaCliente,
            String formalizadoPor, String fechaFormalizacion, String syncStatus, String observaciones,
            List<Map<String, Object>> pruebas, List<Measurement> mediciones, List<String> fotos) {
    }

    public record ProtocolResponse(
            String id, String codigoProtocolo, String visitaId, String ordenTrabajo,
            String equipoCodigo, String tipoEquipo, String fechaHora, String estado, String resultadoGlobal,
            String tecnico, String tecnicoCreador, String descripcionMantenimiento, String instrumento,
            boolean calibracionVigente, String temperaturaAmbiente, String humedad,
            boolean actaEntregadaCliente, String formalizadoPor, String fechaFormalizacion,
            String syncStatus, String observaciones, List<Map<String, Object>> pruebas,
            List<Map<String, Object>> mediciones, List<String> fotos) {
    }

    public record TestRequest(
            String id, String protocoloId, String ordenTrabajo, @NotBlank String equipoCodigo,
            String tipoPrueba, String ubicacion, String fechaHora, String estadoPrueba,
            String motivoNoRealizada, String resultado, String instrumentoId, String instrumento,
            String instrumentoSerie, String instrumentoCalibracion, boolean calibracionVigente,
            String temperaturaAmbiente, String humedad, List<Measurement> mediciones,
            String observaciones, Double variacionHistoricaPorcentaje, boolean superaVariacion25) {
    }

    public record TestResponse(
            String id, String protocoloId, String ordenTrabajo, String equipoCodigo, String tipoPrueba,
            String ubicacion, String fechaHora, String estadoPrueba, String motivoNoRealizada,
            String resultado, String instrumentoId, String instrumento, String instrumentoSerie,
            String instrumentoCalibracion, boolean calibracionVigente, String temperaturaAmbiente,
            String humedad, List<Map<String, Object>> mediciones, String observaciones,
            Double variacionHistoricaPorcentaje, boolean superaVariacion25) {
    }

    public record FormalizeRequest(@NotBlank String usuario) {
    }

    public record ActaRequest(
            String id, @NotBlank String visitaId, String ordenTrabajo, String cliente, String instalacion,
            String fechaEmision, String tecnicoEntrega, String recibidoPor, boolean marcaPendienteFormalizacion,
            String resumenProtocolos, String syncStatus) {
    }

    public record ActaResponse(
            String id, String visitaId, String ordenTrabajo, String cliente, String instalacion, String fechaEmision,
            String tecnicoEntrega, String recibidoPor, boolean marcaPendienteFormalizacion,
            String resumenProtocolos, String syncStatus) {
        static ActaResponse from(ActaEntity entity) {
            return new ActaResponse(entity.getId(), entity.getVisitaId(), entity.getOrdenTrabajo(), entity.getCliente(),
                    entity.getInstalacion(), entity.getFechaEmision(), entity.getTecnicoEntrega(),
                    entity.getRecibidoPor(), entity.isMarcaPendienteFormalizacion(),
                    entity.getResumenProtocolos(), entity.getSyncStatus());
        }
    }
}
