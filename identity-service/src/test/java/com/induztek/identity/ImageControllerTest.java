package com.induztek.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ImageControllerTest {
    @TempDir
    Path uploads;

    private ImageController controller() {
        return new ImageController(uploads.toString(), "http://localhost:8080");
    }

    private static byte[] image(int type, String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void pngWithTransparencyDeclaredAsJpegIsStoredAsPng() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", image(BufferedImage.TYPE_INT_ARGB, "png"));

        ImageController.ImageResponse response = controller().upload(file);

        assertTrue(response.filename().endsWith(".png"));
        assertTrue(Files.exists(uploads.resolve(response.filename())));
    }

    @Test
    void jpegDeclaredAsPngIsStoredAsJpg() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", image(BufferedImage.TYPE_INT_RGB, "jpg"));

        assertTrue(controller().upload(file).filename().endsWith(".jpg"));
    }

    @Test
    void nonImageContentIsRejectedWith415() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", "no soy una imagen".getBytes());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller().upload(file));

        assertEquals(415, error.getStatusCode().value());
    }

    @Test
    void emptyFileIsRejectedWith400() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);

        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller().upload(file)).getStatusCode().value());
    }
}
