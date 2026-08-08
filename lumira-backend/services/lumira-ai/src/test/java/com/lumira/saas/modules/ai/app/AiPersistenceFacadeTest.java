package com.lumira.saas.modules.ai.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.repository.AiKnowledgeBasePersistencePort;
import com.lumira.saas.modules.ai.repository.AiManagementPersistencePort;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.Test;

class AiPersistenceFacadeTest {

    @Test
    void knowledgeBaseFacadeDelegatesToItsPersistencePort() {
        AiKnowledgeBasePersistencePort port = mock(AiKnowledgeBasePersistencePort.class);
        AiKnowledgeBaseAppService service = new AiKnowledgeBaseAppService(port);
        CurrentUser currentUser = mock(CurrentUser.class);
        AiVO.KnowledgeBaseVO expected = new AiVO.KnowledgeBaseVO();
        expected.setId(12L);
        when(port.getKnowledgeBase(currentUser, 12L)).thenReturn(expected);

        assertThat(service.getKnowledgeBase(currentUser, 12L)).isSameAs(expected);
        verify(port).getKnowledgeBase(currentUser, 12L);
    }

    @Test
    void managementFacadeDelegatesToItsPersistencePort() {
        AiManagementPersistencePort port = mock(AiManagementPersistencePort.class);
        AiManagementAppService service = new AiManagementAppService(port);
        CurrentUser currentUser = mock(CurrentUser.class);
        AiVO.EmployeeDetailVO expected = new AiVO.EmployeeDetailVO();
        expected.setId(7L);
        when(port.getEmployee(currentUser, 7L)).thenReturn(expected);

        assertThat(service.getEmployee(currentUser, 7L)).isSameAs(expected);
        verify(port).getEmployee(currentUser, 7L);
    }
}
