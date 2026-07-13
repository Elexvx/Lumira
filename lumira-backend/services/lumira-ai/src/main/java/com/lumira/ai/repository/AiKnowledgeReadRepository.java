package com.lumira.ai.repository;

import com.lumira.ai.vo.AiKnowledgeBaseVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AiKnowledgeReadRepository {

    List<AiKnowledgeBaseVO> findKnowledgeBases(AccessContext access, String keyword, String status, String scope, long limit, long offset);

    Optional<AiKnowledgeBaseVO> findAccessibleKnowledgeBase(Long id, AccessContext access);

    Optional<AiKnowledgeBaseVO> findManageableKnowledgeBase(Long id, AccessContext access);

    List<AiKnowledgeDocumentVO> findDocuments(Long knowledgeBaseId, long limit, long offset);

    Optional<AiKnowledgeDocumentVO> findDocument(Long knowledgeBaseId, Long documentId);

    record AccessContext(Long userId, String userUuid, Set<Long> roleIds, Set<Long> departmentIds, boolean unrestricted) { }
}
