package com.lumira.saas.modules.ai.app;

import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
