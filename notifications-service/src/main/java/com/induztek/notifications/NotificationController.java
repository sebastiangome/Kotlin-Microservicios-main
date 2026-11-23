package com.induztek.notifications;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/notificaciones")
public class NotificationController {
    private final JavaMailSender mailSender;
    private final String sender;

    public NotificationController(JavaMailSender mailSender, @Value("${notifications.sender}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Tag(name = "Uso interno")
    @Operation(summary = "Enviar el correo con el código de recuperación",
            description = "Solo para llamadas entre servicios (lo usa identity-service). No tiene autenticación: es seguro únicamente mientras el puerto del servicio no se publique fuera de la red de Docker.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Correo enviado al servidor SMTP."),
            @ApiResponse(responseCode = "400", description = "Correo con formato inválido o código vacío. Este servicio no usa el manejador global, así que el cuerpo es el formato por defecto de Spring Boot.",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "BAD_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"BAD_REQUEST\", \"message\": \"Validation failed\", \"path\": \"/internal/notificaciones/recuperacion\", \"details\": []}"))),
            @ApiResponse(responseCode = "502", description = "El SMTP rechazó la autenticación o falló el envío.",
                        content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "BAD_GATEWAY", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 502, \"error\": \"Bad Gateway\", \"code\": \"BAD_GATEWAY\", \"message\": \"Email delivery failed\", \"path\": \"/internal/notificaciones/recuperacion\", \"details\": []}")))})
    @PostMapping("/recuperacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendRecoveryEmail(@Valid @RequestBody RecoveryRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(request.email());
        message.setSubject("Código para recuperar tu contraseña");
        message.setText("Tu código de recuperación es " + request.code()
                + ". Expira en 10 minutos. Si no solicitaste el cambio, ignora este correo.");
        try {
            mailSender.send(message);
        } catch (MailAuthenticationException | MailSendException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Email delivery failed", exception);
        }
    }

    public record RecoveryRequest(@Email @NotBlank String email, @NotBlank String code) {
    }
}
