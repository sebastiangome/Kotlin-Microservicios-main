package com.induztek.roles;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Roles Service - Induztek",
                version = "1.0.0",
                description = "Catálogo de roles del sistema (ADMIN, TEST_ENGINEER, FIELD_TECHNICIAN, CLIENT). Todos los errores responden el mismo JSON: timestamp, status, error, code, message, path y details (lista de campo+mensaje para errores de validación). Nunca se incluyen trazas ni detalles internos."),
        servers = {
                @Server(url = "http://localhost:8080", description = "API Gateway (desarrollo local)"),
                @Server(url = "http://10.0.2.2:8080", description = "API Gateway visto desde el emulador de Android")
        },
        tags = {
                @Tag(name = "Roles", description = "Consulta de roles y alta de roles nuevos (solo ADMIN)."),
                @Tag(name = "Uso interno", description = "Endpoints entre servicios, bloqueados desde el API Gateway.")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login. Se envía como: Authorization: Bearer <token>.")
public class OpenApiConfiguration {
}
