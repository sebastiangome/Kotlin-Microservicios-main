package com.induztek.operations;

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
                title = "Operations Service - Induztek",
                version = "1.0.0",
                description = "Visitas, protocolos, pruebas, actas e imágenes de terreno. Todos los errores responden el mismo JSON: timestamp, status, error, code, message, path y details (lista de campo+mensaje para errores de validación). Nunca se incluyen trazas ni detalles internos."),
        servers = {
                @Server(url = "http://localhost:8080", description = "API Gateway (desarrollo local)"),
                @Server(url = "http://10.0.2.2:8080", description = "API Gateway visto desde el emulador de Android")
        },
        tags = {
                @Tag(name = "Visitas", description = "Visitas de terreno por orden de trabajo."),
                @Tag(name = "Protocolos", description = "Protocolos por equipo y su formalización."),
                @Tag(name = "Pruebas", description = "Pruebas y mediciones asociadas a un protocolo."),
                @Tag(name = "Actas", description = "Acta de entrega de una visita (una por visita)."),
                @Tag(name = "Imágenes", description = "Subida y descarga de fotos de terreno.")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login. Se envía como: Authorization: Bearer <token>.")
public class OpenApiConfiguration {
}
