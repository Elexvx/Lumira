package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiKnowledgeBasePersistencePort;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI knowledge-base application boundary.  Authorization and use-case entry
 * points remain here; the compatibility persistence adapter owns storage until
 * the AI runtime is physically extracted.
 */
@Service
public class AiKnowledgeBaseAppService {

    private final AiKnowledgeBasePersistencePort persistenceAdapter;

    public AiKnowledgeBaseAppService(AiKnowledgeBasePersistencePort persistenceAdapter) {
        this.persistenceAdapter = persistenceAdapter;
    }

    public PageResponse<AiVO.KnowledgeBaseVO> listKnowledgeBases(
            CurrentUser currentUser, String keyword, String status, String scope, long pageNo, long pageSize
    ) {
        return persistenceAdapter.listKnowledgeBases(currentUser, keyword, status, scope, pageNo, pageSize);
    }

    public AiVO.KnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        return persistenceAdapter.getKnowledgeBase(currentUser, id);
    }

    public AiVO.KnowledgeBaseVO createKnowledgeBase(CurrentUser currentUser, AiDTO.KnowledgeBaseUpsertRequest request) {
        return persistenceAdapter.createKnowledgeBase(currentUser, request);
    }

    public AiVO.KnowledgeBaseVO updateKnowledgeBase(CurrentUser currentUser, Long id, AiDTO.KnowledgeBaseUpsertRequest request) {
        return persistenceAdapter.updateKnowledgeBase(currentUser, id, request);
    }

    public boolean deleteKnowledgeBase(CurrentUser currentUser, Long id) {
        return persistenceAdapter.deleteKnowledgeBase(currentUser, id);
    }

    public PageResponse<AiVO.KnowledgeDocumentVO> listDocuments(
            CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize
    ) {
        return persistenceAdapter.listDocuments(currentUser, knowledgeBaseId, pageNo, pageSize);
    }

    public AiVO.KnowledgeDocumentVO uploadDocument(CurrentUser currentUser, Long knowledgeBaseId, MultipartFile file) {
        return persistenceAdapter.uploadDocument(currentUser, knowledgeBaseId, file);
    }

    public AiVO.KnowledgeDocumentVO reindexDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        return persistenceAdapter.reindexDocument(currentUser, knowledgeBaseId, documentId);
    }

    public int processPendingIndexTasks(int limit) {
        return persistenceAdapter.processPendingIndexTasks(limit);
    }

    public void processKnowledgeDocumentIndex(Long knowledgeBaseId, Long documentId) {
        persistenceAdapter.processKnowledgeDocumentIndex(knowledgeBaseId, documentId);
    }

    public boolean deleteDocument(CurrentUser currentUser, Long knowledgeBaseId, Long documentId) {
        return persistenceAdapter.deleteDocument(currentUser, knowledgeBaseId, documentId);
    }

    public List<AiVO.KnowledgeReferenceVO> retrieve(
            CurrentUser currentUser, String query, List<Long> knowledgeBaseIds, int limit
    ) {
        return persistenceAdapter.retrieve(currentUser, query, knowledgeBaseIds, limit);
    }

    public List<AiVO.KnowledgeBaseVO> listEmployeeKnowledgeBases(CurrentUser currentUser, Long employeeId) {
        return persistenceAdapter.listEmployeeKnowledgeBases(currentUser, employeeId);
    }

    public boolean updateEmployeeKnowledgeBases(
            CurrentUser currentUser, Long employeeId, AiDTO.EmployeeKnowledgeBasesUpdateRequest request
    ) {
        return persistenceAdapter.updateEmployeeKnowledgeBases(currentUser, employeeId, request);
    }

    public List<AiVO.KnowledgeReferenceVO> retrieveForEmployee(
            CurrentUser currentUser, Long employeeId, String query, int limit
    ) {
        return persistenceAdapter.retrieveForEmployee(currentUser, employeeId, query, limit);
    }
}
