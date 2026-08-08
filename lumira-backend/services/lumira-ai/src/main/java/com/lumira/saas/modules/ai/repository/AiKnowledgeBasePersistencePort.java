package com.lumira.saas.modules.ai.repository;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Persistence port for AI knowledge-base use cases. */
public interface AiKnowledgeBasePersistencePort {

    PageResponse<AiVO.KnowledgeBaseVO> listKnowledgeBases(CurrentUser currentUser, String keyword, String status, String scope, long pageNo, long pageSize);

    AiVO.KnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id);

    AiVO.KnowledgeBaseVO createKnowledgeBase(CurrentUser currentUser, AiDTO.KnowledgeBaseUpsertRequest request);

    AiVO.KnowledgeBaseVO updateKnowledgeBase(CurrentUser currentUser, Long id, AiDTO.KnowledgeBaseUpsertRequest request);

    boolean deleteKnowledgeBase(CurrentUser currentUser, Long id);

    PageResponse<AiVO.KnowledgeDocumentVO> listDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize);

    AiVO.KnowledgeDocumentVO uploadDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file);

    AiVO.KnowledgeDocumentVO reindexDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId);

    int processPendingIndexTasks(int limit);

    void processKnowledgeDocumentIndex(Long knowledgeBaseId, Long documentId);

    boolean deleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId);

    List<AiVO.KnowledgeReferenceVO> retrieve(CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit);

    List<AiVO.KnowledgeBaseVO> listEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId);

    boolean updateEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeKnowledgeBasesUpdateRequest request);

    List<AiVO.KnowledgeReferenceVO> retrieveForEmployee(CurrentUser currentUser, Long employeeId, String query, int limit);
}
