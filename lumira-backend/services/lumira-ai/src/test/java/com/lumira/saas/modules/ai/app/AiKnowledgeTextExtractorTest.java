package com.lumira.saas.modules.ai.app;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AiKnowledgeTextExtractorTest {

    @Test
    void emptyFileKeepsSpecificUserMessage() {
        AiKnowledgeTextExtractor extractor = new AiKnowledgeTextExtractor();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[0]);

        BizException exception = assertThrows(BizException.class, () -> extractor.extract(file));

        assertEquals("请先选择知识库文件", exception.getUserMessage());
    }

    @Test
    void missingExtensionKeepsSpecificUserMessage() {
        AiKnowledgeTextExtractor extractor = new AiKnowledgeTextExtractor();
        MockMultipartFile file = new MockMultipartFile("file", "notes", "text/plain", "hello".getBytes());

        BizException exception = assertThrows(BizException.class, () -> extractor.extract(file));

        assertEquals("知识库文件必须包含格式后缀", exception.getUserMessage());
    }

    @Test
    void rejectsOversizedFileBeforeReadingBytes() {
        AiKnowledgeTextExtractor extractor = new AiKnowledgeTextExtractor();
        MultipartFile file = new OversizedMultipartFile();

        BizException exception = assertThrows(BizException.class, () -> extractor.extract(file));

        assertEquals("知识库文件不能超过 20MB", exception.getUserMessage());
    }

    private static class OversizedMultipartFile implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "large.txt";
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return 20L * 1024L * 1024L + 1L;
        }

        @Override
        public byte[] getBytes() throws IOException {
            fail("Oversized files should be rejected before bytes are read");
            return new byte[0];
        }

        @Override
        public InputStream getInputStream() throws IOException {
            fail("Oversized files should be rejected before streams are read");
            return InputStream.nullInputStream();
        }

        @Override
        public void transferTo(java.io.File dest) {
            fail("Oversized files should be rejected before transfer");
        }
    }
}
