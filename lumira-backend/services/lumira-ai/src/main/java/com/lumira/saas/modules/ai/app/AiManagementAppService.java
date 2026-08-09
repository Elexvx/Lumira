package com.lumira.saas.modules.ai.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.repository.AiManagementPersistencePort;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * AI management application boundary.  It exposes the stable management API
 * while the compatibility persistence adapter owns storage implementation.
 */
@Service
public class AiManagementAppService {

    private final AiManagementPersistencePort persistenceAdapter;

    public AiManagementAppService(AiManagementPersistencePort persistenceAdapter) {
        this.persistenceAdapter = persistenceAdapter;
    }

    public PageResponse<AiVO.EmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        return persistenceAdapter.listEmployees(currentUser, pageNo, pageSize);
    }

    public AiVO.GovernanceOverviewVO governanceOverview(CurrentUser currentUser) {
        return persistenceAdapter.governanceOverview(currentUser);
    }

    public AiVO.EmployeeDetailVO getEmployee(CurrentUser currentUser, Long id) {
        return persistenceAdapter.getEmployee(currentUser, id);
    }

    public AiVO.EmployeeDetailVO createEmployee(CurrentUser currentUser, AiDTO.EmployeeUpsertRequest request) {
        return persistenceAdapter.createEmployee(currentUser, request);
    }

    public AiVO.EmployeeDetailVO updateEmployee(CurrentUser currentUser, Long id, AiDTO.EmployeeUpsertRequest request) {
        return persistenceAdapter.updateEmployee(currentUser, id, request);
    }

    public boolean deleteEmployee(CurrentUser currentUser, Long id) {
        return persistenceAdapter.deleteEmployee(currentUser, id);
    }

    public boolean updateEmployeeEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        return persistenceAdapter.updateEmployeeEnabled(currentUser, id, enabled);
    }

    public AiVO.PromptTemplateVO employeeTemplate(CurrentUser currentUser) {
        return persistenceAdapter.employeeTemplate(currentUser);
    }

    public List<AiVO.EmployeeCapabilityVO> getEmployeeCapabilities(CurrentUser currentUser, Long employeeId) {
        return persistenceAdapter.getEmployeeCapabilities(currentUser, employeeId);
    }

    public boolean updateEmployeeCapabilities(
            CurrentUser currentUser, Long employeeId, AiDTO.EmployeeCapabilitiesUpdateRequest request
    ) {
        return persistenceAdapter.updateEmployeeCapabilities(currentUser, employeeId, request);
    }

    public PageResponse<AiVO.LlmServiceVO> listLlmServices(CurrentUser currentUser, long pageNo, long pageSize) {
        return persistenceAdapter.listLlmServices(currentUser, pageNo, pageSize);
    }

    public AiVO.LlmServiceVO getLlmService(CurrentUser currentUser, Long id) {
        return persistenceAdapter.getLlmService(currentUser, id);
    }

    public AiVO.LlmServiceVO createLlmService(CurrentUser currentUser, AiDTO.LlmServiceUpsertRequest request) {
        return persistenceAdapter.createLlmService(currentUser, request);
    }

    public AiVO.LlmServiceVO updateLlmService(CurrentUser currentUser, Long id, AiDTO.LlmServiceUpsertRequest request) {
        return persistenceAdapter.updateLlmService(currentUser, id, request);
    }

    public boolean deleteLlmService(CurrentUser currentUser, Long id) {
        return persistenceAdapter.deleteLlmService(currentUser, id);
    }

    public boolean updateLlmServiceEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        return persistenceAdapter.updateLlmServiceEnabled(currentUser, id, enabled);
    }

    public AiVO.LlmServiceTestResultVO testLlmService(CurrentUser currentUser, AiDTO.LlmServiceTestRequest request) {
        return persistenceAdapter.testLlmService(currentUser, request);
    }

    public AiVO.EmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        return persistenceAdapter.getAssistantEmployee(currentUser);
    }

    public PageResponse<AiVO.ConversationVO> listConversations(
            CurrentUser currentUser, Long employeeId, long pageNo, long pageSize
    ) {
        return persistenceAdapter.listConversations(currentUser, employeeId, pageNo, pageSize);
    }

    public List<AiVO.MessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        return persistenceAdapter.listConversationMessages(currentUser, conversationId);
    }

    public boolean updateConversation(CurrentUser currentUser, Long conversationId, AiDTO.ConversationUpdateRequest request) {
        return persistenceAdapter.updateConversation(currentUser, conversationId, request);
    }

    public boolean deleteConversation(CurrentUser currentUser, Long conversationId) {
        return persistenceAdapter.deleteConversation(currentUser, conversationId);
    }

    public AiVO.ConversationShareVO createConversationShare(CurrentUser currentUser, Long conversationId) {
        return persistenceAdapter.createConversationShare(currentUser, conversationId);
    }

    public AiVO.ConversationShareDetailVO getConversationShare(CurrentUser currentUser, String shareToken) {
        return persistenceAdapter.getConversationShare(currentUser, shareToken);
    }

    public AiVO.ConversationExportVO exportConversation(CurrentUser currentUser, Long conversationId, String format) {
        return persistenceAdapter.exportConversation(currentUser, conversationId, format);
    }

    public AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request) {
        return persistenceAdapter.chat(currentUser, request);
    }

    public AiVO.ChatResponseVO streamChat(
            CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent
    ) {
        return persistenceAdapter.streamChat(currentUser, request, onEvent);
    }
}
