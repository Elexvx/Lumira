package com.lumira.file.upload;

import com.lumira.common.exception.BizException;
import com.lumira.file.config.UploadProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentUploadServiceTest {

    @Test
    void acceptsOctetStreamWhenExtensionAndContentAreValid() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("document-upload-test").toString());
        DocumentUploadService service = service(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "application/octet-stream",
                "hello knowledge base".getBytes()
        );

        DocumentUploadService.StoredDocument storedDocument = service.upload(file);

        assertEquals("txt", storedDocument.fileExtension());
        assertEquals("text/plain", storedDocument.contentType());
        assertTrue(storedDocument.publicUrl().startsWith("/api/uploads/"));
    }

    @Test
    void uploadFailureKeepsSpecificUserMessage() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempFile("document-upload-test", ".tmp").toString());
        DocumentUploadService service = service(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello knowledge base".getBytes()
        );

        BizException exception = assertThrows(BizException.class, () -> service.upload(file));

        assertEquals("文件上传失败，请检查存储空间配置或稍后重试", exception.getUserMessage());
    }

    @Test
    void acceptsOpenXmlWhenClientSendsZipContentType() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("document-upload-test").toString());
        DocumentUploadService service = service(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/zip",
                openXmlBytes("word/document.xml")
        );

        DocumentUploadService.StoredDocument storedDocument = service.upload(file);

        assertEquals("docx", storedDocument.fileExtension());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", storedDocument.contentType());
    }

    @Test
    void rejectsOpenXmlWithoutContentTypesEntry() throws Exception {
        UploadProperties properties = new UploadProperties();
        properties.setStorageRoot(Files.createTempDirectory("document-upload-test").toString());
        DocumentUploadService service = service(properties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.docx",
                "application/zip",
                openXmlBytesWithoutContentTypes("word/document.xml")
        );

        assertThrows(BizException.class, () -> service.upload(file));
    }

    private byte[] openXmlBytes(String entryName) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] openXmlBytesWithoutContentTypes(String entryName) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private DocumentUploadService service(UploadProperties properties) {
        return new DocumentUploadService(
                properties,
                new FileStorageMetrics(new SimpleMeterRegistry()),
                new ZipSafetyValidator(properties)
        );
    }
}
