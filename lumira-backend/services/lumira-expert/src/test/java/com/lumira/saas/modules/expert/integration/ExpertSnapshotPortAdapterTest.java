package com.lumira.saas.modules.expert.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lumira.api.expert.ExpertSnapshot;
import com.lumira.saas.modules.expert.repository.ExpertRepository;
import com.lumira.saas.modules.expert.vo.ExpertVO;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExpertSnapshotPortAdapterTest {

    @Test
    void exposesReviewerContactDataThroughTheExpertOwnerPort() {
        ExpertRepository repository = mock(ExpertRepository.class);
        ExpertVO.Expert expert = new ExpertVO.Expert();
        expert.setId(80L);
        expert.setUserId(18L);
        expert.setUserUuid("expert-user-uuid");
        expert.setName("评审专家");
        expert.setEmail("expert@example.com");
        expert.setStatus("active");
        expert.setApprovalStatus("APPROVED");
        expert.setAccountStatus("ENABLED");
        when(repository.findById(80L)).thenReturn(Optional.of(expert));

        ExpertSnapshot snapshot = new ExpertSnapshotPortAdapter(repository).findExpertSnapshot(80L);

        assertThat(snapshot).isEqualTo(new ExpertSnapshot(
                80L, 18L, "expert-user-uuid", "评审专家", "expert@example.com",
                "active", "APPROVED", "ENABLED"
        ));
    }
}
