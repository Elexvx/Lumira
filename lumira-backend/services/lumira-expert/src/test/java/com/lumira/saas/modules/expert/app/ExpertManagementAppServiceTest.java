package com.lumira.saas.modules.expert.app;

import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.api.workflow.WorkflowStartPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.saas.modules.expert.dto.ExpertDTO;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertManagementAppServiceTest {

    @Test
    void competitionApplicationPersistsDynamicValuesAndChecksConfiguredRequiredFields() {
        ExpertRepository repository = mock(ExpertRepository.class);
        WorkflowStartPort workflowStartPort = mock(WorkflowStartPort.class);
        ExpertVO.Expert stored = expert(72L, "pending", "PENDING");
        when(repository.isPublishedCompetition("competition-uuid")).thenReturn(true);
        when(repository.findPublishedCompetitionExpertFields("competition-uuid")).thenReturn(List.of(
                new ExpertRepository.ExpertApplicationField("portfolio", "代表作品", "{}", true, true)
        ));
        when(repository.create(any(), anyString(), anyString(), anyLong(), anyString())).thenReturn(72L);
        when(workflowStartPort.startWorkflow(any(), anyString(), anyLong(), anyString(), anyString(), anyMap())).thenReturn(902L);
        when(repository.attachWorkflow(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(repository.findById(72L)).thenReturn(Optional.of(stored));
        ExpertManagementAppService service = new ExpertManagementAppService(repository, workflowStartPort, dictionary());

        ExpertDTO.ExpertUpsertRequest request = request();
        request.setCompetitionUuid("competition-uuid");
        request.setExpertise("人工智能与机器人");
        request.setExtraValues(Map.of("portfolio", "智能制造平台"));

        ExpertVO.Expert result = service.createExpert(user("expert:apply"), request);

        assertThat(result.getId()).isEqualTo(72L);
        verify(repository).create(
                org.mockito.ArgumentMatchers.argThat(value -> "competition-uuid".equals(value.getCompetitionUuid())
                        && value.getExtraValuesJson().contains("智能制造平台")),
                anyString(), anyString(), anyLong(), anyString()
        );
    }

    @Test
    void competitionApplicationRejectsMissingConfiguredRequiredField() {
        ExpertRepository repository = mock(ExpertRepository.class);
        when(repository.isPublishedCompetition("competition-uuid")).thenReturn(true);
        when(repository.findPublishedCompetitionExpertFields("competition-uuid")).thenReturn(List.of(
                new ExpertRepository.ExpertApplicationField("portfolio", "代表作品", "{}", true, true)
        ));
        ExpertManagementAppService service = new ExpertManagementAppService(repository, mock(WorkflowStartPort.class), dictionary());
        ExpertDTO.ExpertUpsertRequest request = request();
        request.setCompetitionUuid("competition-uuid");
        request.setExpertise("人工智能与机器人");

        assertThatThrownBy(() -> service.createExpert(user("expert:apply"), request))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(repository, org.mockito.Mockito.never()).create(any(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void createExpertStartsWorkflowAndAttachesItUsingExpertOwnedRepository() {
        ExpertRepository repository = mock(ExpertRepository.class);
        WorkflowStartPort workflowStartPort = mock(WorkflowStartPort.class);
        ExpertVO.Expert stored = expert(71L, "pending", "PENDING");
        when(repository.create(any(), anyString(), anyString(), anyLong(), anyString())).thenReturn(71L);
        when(workflowStartPort.startWorkflow(any(), anyString(), anyLong(), anyString(), anyString(), anyMap())).thenReturn(901L);
        when(repository.attachWorkflow(anyLong(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString()))
                .thenReturn(1);
        when(repository.findById(71L)).thenReturn(Optional.of(stored));
        ExpertManagementAppService service = new ExpertManagementAppService(repository, workflowStartPort, dictionary());

        ExpertVO.Expert result = service.createExpert(user("expert:apply"), request());

        assertThat(result.getId()).isEqualTo(71L);
        verify(workflowStartPort).startWorkflow(
                any(CurrentUser.class),
                eq(WorkflowStartPort.BUSINESS_EXPERT_APPLICATION),
                eq(71L),
                anyString(),
                eq("Alice"),
                anyMap()
        );
        verify(repository).attachWorkflow(eq(71L), anyString(), eq("pending"), eq("PENDING"), eq(901L), eq(41L), eq("user-uuid-41"));
    }

    @Test
    void listExpertsRejectsCallerWithoutViewPermission() {
        ExpertManagementAppService service = new ExpertManagementAppService(
                mock(ExpertRepository.class), mock(WorkflowStartPort.class), dictionary()
        );

        assertThatThrownBy(() -> service.listExperts(user("expert:update"), null, null, null, 1, 20))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void strictServiceRefreshesCurrentUserThroughSharedTrustResolver() {
        ExpertRepository repository = mock(ExpertRepository.class);
        WorkflowStartPort workflowStartPort = mock(WorkflowStartPort.class);
        TrustedCurrentUserResolver resolver = mock(TrustedCurrentUserResolver.class);
        CurrentUser stale = user("expert:view");
        CurrentUser fresh = user("expert:view");
        fresh.setUsername("refreshed-expert-admin");
        when(resolver.resolve(stale)).thenReturn(fresh);
        when(repository.search(null, null, null, 0L, 10L))
                .thenReturn(new ExpertRepository.PageData(List.of(), 0L));
        ExpertManagementAppService service = new ExpertManagementAppService(
                repository, workflowStartPort, dictionary(), resolver
        );

        service.listExperts(stale, null, null, null, 1, 10);

        assertThat(stale.getUsername()).isEqualTo("refreshed-expert-admin");
        verify(resolver, atLeastOnce()).resolve(stale);
    }

    @Test
    void createExpertRejectsUnauthenticatedApplicant() {
        CurrentUser anonymous = user("expert:apply");
        anonymous.setAuthenticated(false);
        ExpertManagementAppService service = new ExpertManagementAppService(
                mock(ExpertRepository.class), mock(WorkflowStartPort.class), dictionary()
        );

        assertThatThrownBy(() -> service.createExpert(anonymous, request()))
                .isInstanceOf(BizException.class)
                .extracting(error -> ((BizException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private static DictionaryValueNormalizer dictionary() {
        Map<String, List<String>> values = Map.of(
                "aiadc_expert_status", List.of("pending", "active"),
                "aiadc_expert_initial_status", List.of("pending"),
                "aiadc_expert_approval_status", List.of("PENDING", "APPROVED"),
                "aiadc_expert_expertise", List.of("Java"),
                "aiadc_expert_title", List.of("Architect"),
                "aiadc_expert_position", List.of("Engineer"),
                "aiadc_expert_tag", List.of("backend")
        );
        return new DictionaryValueNormalizer() {
            @Override
            public List<String> enabledValues(String dictionaryCode) {
                return values.getOrDefault(dictionaryCode, List.of());
            }

            @Override
            public String normalizeValue(String dictionaryCode, String value, String defaultValue,
                                         boolean fallbackAllowed, String errorMessage) {
                return value == null ? defaultValue : value.trim();
            }
        };
    }

    private static ExpertDTO.ExpertUpsertRequest request() {
        ExpertDTO.ExpertUpsertRequest request = new ExpertDTO.ExpertUpsertRequest();
        request.setName("Alice");
        request.setExpertise("Java");
        request.setEmail("alice@example.com");
        request.setMobile("13800138000");
        return request;
    }

    private static ExpertVO.Expert expert(Long id, String status, String approvalStatus) {
        ExpertVO.Expert expert = new ExpertVO.Expert();
        expert.setId(id);
        expert.setCode("exp-" + id);
        expert.setStatus(status);
        expert.setApprovalStatus(approvalStatus);
        return expert;
    }

    private static CurrentUser user(String permission) {
        CurrentUser user = new CurrentUser();
        user.setUserId(41L);
        user.setUserUuid("user-uuid-41");
        user.setUsername("expert-admin");
        user.setSessionId("session-41");
        user.setSessionVersion(1);
        user.setPermissionsVersion("permissions-41");
        user.setAuthenticated(true);
        user.setPermissions(Set.of(permission));
        return user;
    }
}
