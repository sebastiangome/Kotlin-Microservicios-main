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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/imagenes", "/api/usuarios/imagenes"})
public class ImageController {
    private final Path uploadDirectory;
    private final String publicBaseUrl;

    public ImageController(
            @Value("${media.upload-dir:/app/upload}") String uploadDirectory,
            @Value("${media.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Tag(name = "Imágenes")
    @Operation(summary = "Subir una imagen",
            description = "Recibe multipart/form-data con el campo file (JPEG o PNG, máximo 10 MB). El formato se detecta por el contenido real del archivo, no por el tipo que declare el cliente. Devuelve la URL pública para usarla en perfiles, protocolos o pruebas.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen guardada.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImageController.ImageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente, inválido o vencido. Esta respuesta la genera Spring Security antes del controlador, por eso no trae cuerpo JSON: solo el encabezado WWW-Authenticate."),
            @ApiResponse(responseCode = "400", description = "El archivo está vacío o falta la parte file del formulario.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "BAD_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"BAD_REQUEST\", \"message\": \"Image file is empty\", \"path\": \"/api/imagenes\", \"details\": []}"))),
            @ApiResponse(responseCode = "413", description = "La imagen supera el máximo de 10 MB.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "PAYLOAD_TOO_LARGE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 413, \"error\": \"Payload Too Large\", \"code\": \"PAYLOAD_TOO_LARGE\", \"message\": \"Uploaded file exceeds the maximum allowed size\", \"path\": \"/api/imagenes\", \"details\": []}"))),
            @ApiResponse(responseCode = "415", description = "El archivo no es una imagen JPEG o PNG válida.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "UNSUPPORTED_MEDIA_TYPE", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 415, \"error\": \"Unsupported Media Type\", \"code\": \"UNSUPPORTED_MEDIA_TYPE\", \"message\": \"Only valid JPEG or PNG images are accepted\", \"path\": \"/api/imagenes\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/imagenes\", \"details\": []}")))})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ImageResponse upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = input == null ? Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only valid JPEG or PNG images are accepted");
            }
            ImageReader reader = readers.next();
            // El formato se detecta por el contenido real del archivo, no por el Content-Type que declara el cliente.
            String format = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                case "png" -> "png";
                case "jpeg", "jpg" -> "jpg";
                default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only valid JPEG or PNG images are accepted");
            };
            BufferedImage image;
            try {
                reader.setInput(input);
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
            String filename = UUID.randomUUID() + "." + format;
            Files.createDirectories(uploadDirectory);
            Path destination = uploadDirectory.resolve(filename).normalize();
            if (!destination.getParent().equals(uploadDirectory)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image path");
            }
            if (!ImageIO.write(image, format, destination.toFile())) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image format");
            }
            return new ImageResponse(filename, publicBaseUrl + "/api/imagenes/" + filename);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store image", exception);
        }
    }

    @Tag(name = "Imágenes")
    @Operation(summary = "Descargar una imagen",
            description = "Público: las imágenes se muestran sin token. El nombre debe ser un UUID con extensión jpg o png.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contenido de la imagen.",
                        content = {@Content(mediaType = "image/jpeg"), @Content(mediaType = "image/png")}),
            @ApiResponse(responseCode = "400", description = "El nombre del archivo no tiene el formato esperado.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "BAD_REQUEST", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 400, \"error\": \"Bad Request\", \"code\": \"BAD_REQUEST\", \"message\": \"Invalid image name\", \"path\": \"/api/imagenes/x\", \"details\": []}"))),
            @ApiResponse(responseCode = "404", description = "No existe una imagen con ese nombre.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "NOT_FOUND", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 404, \"error\": \"Not Found\", \"code\": \"NOT_FOUND\", \"message\": \"Image not found\", \"path\": \"/api/imagenes/9f1c2b7e-0a4d-4c1e-8f3b-2d6a5e7c9b10.png\", \"details\": []}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado del servidor. El cuerpo nunca incluye trazas ni detalles internos; el detalle queda solo en el log.",
                        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class), examples = @ExampleObject(name = "INTERNAL_ERROR", value = "{\"timestamp\": \"2026-11-23T21:30:00Z\", \"status\": 500, \"error\": \"Internal Server Error\", \"code\": \"INTERNAL_ERROR\", \"message\": \"Unexpected server error\", \"path\": \"/api/imagenes/x\", \"details\": []}")))})
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> get(@PathVariable String filename) {
        if (!filename.matches("[a-fA-F0-9-]{36}\\.(jpg|png)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image name");
        }
        Path file = uploadDirectory.resolve(filename).normalize();
        if (!file.getParent().equals(uploadDirectory) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        MediaType contentType = filename.toLowerCase(Locale.ROOT).endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(contentType)
                .body(new FileSystemResource(file));
    }

    public record ImageResponse(String filename, String url) {
    }
}
