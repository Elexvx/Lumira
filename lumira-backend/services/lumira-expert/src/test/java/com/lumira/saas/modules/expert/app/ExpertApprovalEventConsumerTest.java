package com.lumira.saas.modules.expert.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.event.EventConsumptionPort;
import com.lumira.api.expert.ExpertAccountProvisioningPort;
import com.lumira.api.expert.ExpertApprovalEventHandler.ExpertApprovalEvent;
import com.lumira.api.workflow.WorkflowEventTypes;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedUserSnapshotResolver;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository.ExpertAccountRecord;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpertApprovalEventConsumerTest {

    @Test
    void approvedExpertCreatesAccountBindsItThenSendsActivation() {
        ExpertApprovalRepository repository = mock(ExpertApprovalRepository.class);
        TrustedUserSnapshotResolver resolver = mock(TrustedUserSnapshotResolver.class);
        ExpertAccountProvisioningPort accounts = mock(ExpertAccountProvisioningPort.class);
        EventConsumptionPort consumption = consumingImmediately();
        ExpertApprovalEventConsumer consumer = new ExpertApprovalEventConsumer(
                repository, new ObjectMapper(), resolver, accounts, consumption
        );
        when(resolver.resolve(anyLong(), anyString(), any(), anyString(), anyString())).thenReturn(operator());
        when(repository.findApprovedExpert(88L, "expert-code-88", 1201L)).thenReturn(Optional.of(expertWithoutAccount()));
        when(accounts.findAccount(41L)).thenReturn(null);
        when(accounts.usernameExists(anyString())).thenReturn(false);
        ExpertAccountProvisioningPort.AccountIdentity account =
                new ExpertAccountProvisioningPort.AccountIdentity(901L, "account-uuid-901", "expert_exp_88");
        when(accounts.createExpertAccount(any(), any())).thenReturn(account);
        when(repository.bindAccount(anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(1);

        consumer.handle(event());

        InOrder order = inOrder(repository, accounts);
        order.verify(accounts).createExpertAccount(any(), any());
        order.verify(repository).bindAccount(anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyString(), any());
        order.verify(accounts).ensureExpertRole(any(), any());
        order.verify(accounts).sendActivation(any(), anyLong(), anyString(), any());
    }

    @Test
    void approvedExpertReusesVerifiedApplicantWithoutActivationEmail() {
        ExpertApprovalRepository repository = mock(ExpertApprovalRepository.class);
        TrustedUserSnapshotResolver resolver = mock(TrustedUserSnapshotResolver.class);
        ExpertAccountProvisioningPort accounts = mock(ExpertAccountProvisioningPort.class);
        EventConsumptionPort consumption = consumingImmediately();
        ExpertApprovalEventConsumer consumer = new ExpertApprovalEventConsumer(
                repository, new ObjectMapper(), resolver, accounts, consumption
        );
        CurrentUser operator = operator();
        ExpertAccountProvisioningPort.AccountIdentity applicant =
                new ExpertAccountProvisioningPort.AccountIdentity(41L, "applicant-uuid", "applicant");
        when(resolver.resolve(anyLong(), anyString(), any(), anyString(), anyString())).thenReturn(operator);
        when(repository.findApprovedExpert(88L, "expert-code-88", 1201L)).thenReturn(Optional.of(expertWithoutAccount()));
        when(accounts.findAccount(41L)).thenReturn(applicant);
        when(repository.bindExistingAccount(anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(1);

        consumer.handle(event());

        verify(accounts, never()).createExpertAccount(any(), any());
        verify(accounts).ensureExpertRole(operator, applicant);
        verify(accounts, never()).sendActivation(any(), anyLong(), anyString(), any());
    }

    @Test
    void invalidSourceIsRejectedBeforeAnyConsumptionReceipt() {
        EventConsumptionPort consumption = mock(EventConsumptionPort.class);
        ExpertApprovalEventConsumer consumer = new ExpertApprovalEventConsumer(
                mock(ExpertApprovalRepository.class), new ObjectMapper(), mock(TrustedUserSnapshotResolver.class),
                mock(ExpertAccountProvisioningPort.class), consumption
        );
        ExpertApprovalEvent invalid = new ExpertApprovalEvent(
                1L, 77L, "operator-uuid", "AI", WorkflowEventTypes.EXPERT_APPROVED,
                WorkflowEventTypes.EXPERT_APPROVED + ":aiadc_expert:88", "{}"
        );

        assertThatThrownBy(() -> consumer.handle(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourceType");
        verify(consumption, never()).executeOnce(any(), any());
    }

    @Test
    void duplicateReceiptDoesNotProvisionAccountAgain() {
        EventConsumptionPort consumption = mock(EventConsumptionPort.class);
        when(consumption.executeOnce(any(), any())).thenReturn(false);
        ExpertApprovalEventConsumer consumer = new ExpertApprovalEventConsumer(
                mock(ExpertApprovalRepository.class), new ObjectMapper(), mock(TrustedUserSnapshotResolver.class),
                mock(ExpertAccountProvisioningPort.class), consumption
        );

        consumer.handle(event());

        verify(consumption).executeOnce(any(), any());
    }

    private static EventConsumptionPort consumingImmediately() {
        EventConsumptionPort port = mock(EventConsumptionPort.class);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        }).when(port).executeOnce(any(), any());
        return port;
    }

    private static ExpertAccountRecord expertWithoutAccount() {
        return new ExpertAccountRecord(
                88L, "expert-code-88", "Expert Alice", "13800138000", "expert@example.com",
                1201L, null, null, 41L, "applicant-uuid"
        );
    }

    private static ExpertApprovalEvent event() {
        return new ExpertApprovalEvent(
                7001L,
                77L,
                "operator-uuid",
                "SYSTEM",
                WorkflowEventTypes.EXPERT_APPROVED,
                WorkflowEventTypes.EXPERT_APPROVED + ":aiadc_expert:88",
                "{\"aggregateId\":88,\"workflowInstanceId\":1201,\"businessUuid\":\"expert-code-88\",\"userUuid\":\"operator-uuid\"}"
        );
    }

    private static CurrentUser operator() {
        CurrentUser user = new CurrentUser();
        user.setUserId(77L);
        user.setUserUuid("operator-uuid");
        user.setUsername("workflow-admin");
        user.setAuthenticated(true);
        user.setPermissions(Set.of("system:user:create"));
        return user;
    }
}
