package com.lumira.saas.modules.ai.repository;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import java.util.function.Consumer;

/** Persistence port for AI management, conversation, and chat projections. */
public interface AiManagementPersistencePort {

    PageResponse<AiVO.EmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize);
    AiVO.GovernanceOverviewVO governanceOverview(CurrentUser currentUser);
    AiVO.EmployeeDetailVO getEmployee(CurrentUser currentUser, Long id);
    AiVO.EmployeeDetailVO createEmployee(CurrentUser currentUser, AiDTO.EmployeeUpsertRequest request);
    AiVO.EmployeeDetailVO updateEmployee(CurrentUser currentUser, Long id, AiDTO.EmployeeUpsertRequest request);
    boolean deleteEmployee(CurrentUser currentUser, Long id);
    boolean updateEmployeeEnabled(CurrentUser currentUser, Long id, boolean enabled);
    AiVO.PromptTemplateVO employeeTemplate(CurrentUser currentUser);
    List<AiVO.EmployeeCapabilityVO> getEmployeeCapabilities(CurrentUser currentUser, Long employeeId);
    boolean updateEmployeeCapabilities(CurrentUser currentUser, Long employeeId, AiDTO.EmployeeCapabilitiesUpdateRequest request);
    PageResponse<AiVO.LlmServiceVO> listLlmServices(CurrentUser currentUser, long pageNo, long pageSize);
    AiVO.LlmServiceVO getLlmService(CurrentUser currentUser, Long id);
    AiVO.LlmServiceVO createLlmService(CurrentUser currentUser, AiDTO.LlmServiceUpsertRequest request);
    AiVO.LlmServiceVO updateLlmService(CurrentUser currentUser, Long id, AiDTO.LlmServiceUpsertRequest request);
    boolean deleteLlmService(CurrentUser currentUser, Long id);
    boolean updateLlmServiceEnabled(CurrentUser currentUser, Long id, boolean enabled);
    AiVO.LlmServiceTestResultVO testLlmService(CurrentUser currentUser, AiDTO.LlmServiceTestRequest request);
    AiVO.EmployeeVO getAssistantEmployee(CurrentUser currentUser);
    PageResponse<AiVO.ConversationVO> listConversations(CurrentUser currentUser, Long employeeId, long pageNo, long pageSize);
    List<AiVO.MessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId);
    boolean updateConversation(CurrentUser currentUser, Long conversationId, AiDTO.ConversationUpdateRequest request);
    boolean deleteConversation(CurrentUser currentUser, Long conversationId);
    AiVO.ConversationShareVO createConversationShare(CurrentUser currentUser, Long conversationId);
    AiVO.ConversationShareDetailVO getConversationShare(CurrentUser currentUser, String shareToken);
    AiVO.ConversationExportVO exportConversation(CurrentUser currentUser, Long conversationId, String format);
    AiVO.ChatResponseVO chat(CurrentUser currentUser, AiDTO.ChatRequest request);
    AiVO.ChatResponseVO streamChat(CurrentUser currentUser, AiDTO.ChatRequest request, Consumer<AiVO.ChatStreamEventVO> onEvent);
}
