package com.lumira.file.upload;

import com.lumira.file.config.UploadProperties;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageUploadServiceTest {

    @Test
    void rejectsSvgUploads() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("image-upload-test").toString());
        ImageUploadService service = new ImageUploadService(properties);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes()
        );

        BizException exception = assertThrows(BizException.class, () -> service.upload(file));
        assertEquals("仅支持 PNG、JPG、GIF、BMP 图片，禁止上传 SVG", exception.getMessage());
    }

    @Test
    void acceptsRasterImageUploads() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("image-upload-test").toString());
        ImageUploadService service = new ImageUploadService(properties);

        byte[] pngBytes = generatePngBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                pngBytes
        );

        ImageUploadService.StoredImage storedImage = service.upload(file);

        assertTrue(storedImage.publicUrl().startsWith("/api/uploads/"));
        assertTrue(storedImage.fileExtension().equals(".png"));
    }

    private byte[] generatePngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
