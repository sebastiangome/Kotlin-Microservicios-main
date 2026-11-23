package com.induztek.notifications;

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
                title = "Notifications Service - Induztek",
                version = "1.0.0",
                description = "Envío de correos del sistema. Servicio interno: no se expone por el API Gateway."),
        servers = {
                @Server(url = "http://localhost:8080", description = "API Gateway (desarrollo local)"),
                @Server(url = "http://10.0.2.2:8080", description = "API Gateway visto desde el emulador de Android")
        },
        tags = {
                @Tag(name = "Uso interno", description = "Solo para llamadas entre servicios dentro de la red de Docker.")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login. Se envía como: Authorization: Bearer <token>.")
public class OpenApiConfiguration {
}
