package com.induztek.catalog;

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
                title = "Catalog Service - Induztek",
                version = "1.0.0",
                description = "Equipos eléctricos de los clientes e instrumentos de medición con su calibración. Todos los errores responden el mismo JSON: timestamp, status, error, code, message, path y details (lista de campo+mensaje para errores de validación). Nunca se incluyen trazas ni detalles internos."),
        servers = {
                @Server(url = "http://localhost:8080", description = "API Gateway (desarrollo local)"),
                @Server(url = "http://10.0.2.2:8080", description = "API Gateway visto desde el emulador de Android")
        },
        tags = {
                @Tag(name = "Equipos", description = "Alta, consulta y modificación de equipos. Los CLIENT solo ven los de su empresa."),
                @Tag(name = "Instrumentos", description = "Instrumentos de medición y vencimiento de calibración. No disponible para CLIENT."),
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
