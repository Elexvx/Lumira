package com.lumira.saas.modules.expert.integration.account;

import com.lumira.api.expert.ExpertAccountActivationPort;
import com.lumira.saas.modules.expert.repository.ExpertApprovalRepository;

/** Expert-owned completion of the account-activation state transition. */
public class ExpertAccountActivationAdapter implements ExpertAccountActivationPort {
    private final ExpertApprovalRepository approvalRepository;

    public ExpertAccountActivationAdapter(ExpertApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Override
    public int activate(ExpertAccountActivation activation) {
        return approvalRepository.activateAccount(
                activation.expertId(), activation.userId(), activation.userUuid(), activation.activatedAt()
        );
    }
}
