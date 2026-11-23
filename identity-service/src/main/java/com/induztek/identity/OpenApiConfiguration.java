package com.induztek.identity;

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
                title = "Identity Service - Induztek",
                version = "1.0.0",
                description = "Registro, inicio de sesión, recuperación de contraseña, administración de cuentas e imágenes de perfil. Todos los errores responden el mismo JSON: timestamp, status, error, code, message, path y details (lista de campo+mensaje para errores de validación). Nunca se incluyen trazas ni detalles internos."),
        servers = {
                @Server(url = "http://localhost:8080", description = "API Gateway (desarrollo local)"),
                @Server(url = "http://10.0.2.2:8080", description = "API Gateway visto desde el emulador de Android")
        },
        tags = {
                @Tag(name = "Autenticación", description = "Registro e inicio de sesión. Devuelven o preparan el token JWT."),
                @Tag(name = "Recuperación de contraseña", description = "Código de 6 dígitos por correo, con límite de 5 intentos."),
                @Tag(name = "Usuarios", description = "Consulta y edición de la propia cuenta."),
                @Tag(name = "Administración de usuarios", description = "Solo ADMIN: listar, aprobar, desactivar, cambiar de rol y eliminar cuentas."),
                @Tag(name = "Imágenes", description = "Subida y descarga de fotos JPEG o PNG.")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtenido en POST /api/auth/login. Se envía como: Authorization: Bearer <token>.")
public class OpenApiConfiguration {
}
