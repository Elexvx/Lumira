package com.lumira.ai.repository;

import com.lumira.ai.vo.AiConversationVO;
import com.lumira.ai.vo.AiMessageAttachmentVO;
import com.lumira.ai.vo.AiMessageVO;
import java.util.List;
import java.util.Map;

public interface AiConversationReadRepository {

    List<AiConversationVO> findConversations(Long ownerUserId, String ownerUserUuid, Long employeeId, long limit, long offset);

    boolean existsOwnedConversation(Long ownerUserId, String ownerUserUuid, Long conversationId);

    List<AiMessageVO> findMessages(Long conversationId, int limit);

    Map<Long, List<AiMessageAttachmentVO>> findAttachmentsByMessage(Long conversationId);
}
