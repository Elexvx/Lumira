package com.legendary.invention.saas.infrastructure.upload;

import com.legendary.invention.saas.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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
        assertEquals("仅支持常见图片格式", exception.getErrorMessage());
    }

    @Test
    void acceptsRasterImageUploads() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("image-upload-test").toString());
        ImageUploadService service = new ImageUploadService(properties);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String publicPath = service.upload(file);

        assertTrue(publicPath.startsWith("/api/uploads/"));
        assertTrue(publicPath.endsWith(".png"));
    }
}
