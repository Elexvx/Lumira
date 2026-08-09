package com.lumira.saas.modules.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.workflow.dto.WorkflowDTO;
import com.lumira.saas.modules.workflow.infrastructure.persistence.WorkflowRowMapper;
import com.lumira.saas.modules.workflow.infrastructure.persistence.WorkflowSqlOperations;
import com.lumira.saas.modules.workflow.repository.WorkflowRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdbcWorkflowRepositoryTest {

    @Test
    void saveDraftWritesDefinitionThenGraphThroughTheWorkflowOwnedPersistenceAdapter() {
        WorkflowSqlOperations database = mock(WorkflowSqlOperations.class);
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        when(database.queryForObject(eq("select last_insert_id()"), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(71L);
        JdbcWorkflowRepository repository = repository(database);

        WorkflowRepository.DraftSaveResult saved = repository.saveDraft(
                null,
                "EXPERT_APPLICATION",
                definition(),
                new WorkflowRepository.Actor(1001L, "user-uuid-1001"),
                LocalDateTime.of(2026, 8, 8, 10, 0)
        );

        assertThat(saved.definitionId()).isEqualTo(71L);
        assertThat(saved.definitionWriteCount()).isEqualTo(1);
        assertThat(saved.nodeWriteCounts()).containsExactly(1, 1);
        assertThat(saved.edgeWriteCounts()).containsExactly(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database, org.mockito.Mockito.times(4)).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("insert into workflow_definition"));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("insert into workflow_node"));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("insert into workflow_edge"));
    }

    @Test
    void publishDefinitionKeepsTheOptimisticBusinessStatusAndVersionPredicate() {
        WorkflowSqlOperations database = mock(WorkflowSqlOperations.class);
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        JdbcWorkflowRepository repository = repository(database);

        int updated = repository.publishDefinition(
                new WorkflowRepository.DefinitionBoundary(11L, "EXPERT_APPLICATION", "DRAFT", 4),
                new WorkflowRepository.Actor(1001L, "user-uuid-1001"),
                LocalDateTime.of(2026, 8, 8, 10, 0)
        );

        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getValue()).contains("and business_type = ?", "and status = ?", "and version_no = ?");
    }

    @Test
    void taskQueriesAndWritesKeepUserUuidAndRoleAssignmentInsideTheWorkflowAdapter() {
        WorkflowSqlOperations database = mock(WorkflowSqlOperations.class);
        when(database.queryForObject(anyString(), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(2L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<WorkflowRowMapper<Object>>any(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(List.of());
        when(database.update(anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1);
        JdbcWorkflowRepository repository = repository(database);
        WorkflowRepository.TaskAssignment assignment = new WorkflowRepository.TaskAssignment(1001L, "user-uuid-1001", Set.of(3001L));

        WorkflowRepository.TaskPage page = repository.findTasks(assignment, "PENDING", 0, 20);
        int updated = repository.completeTask(
                99L,
                assignment,
                new WorkflowRepository.TaskCompletion(
                        "APPROVED", new WorkflowRepository.Actor(1001L, "user-uuid-1001"), "alice",
                        LocalDateTime.of(2026, 8, 8, 10, 0), "ok"
                )
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).isEmpty();
        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        verify(database).queryForObject(org.mockito.ArgumentMatchers.contains("t.approver_user_uuid = ?"), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class));
        verify(database).query(org.mockito.ArgumentMatchers.contains("t.approver_role_id in"), org.mockito.ArgumentMatchers.<WorkflowRowMapper<Object>>any(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues()).anySatisfy(statement -> assertThat(statement).contains("approver_user_uuid = ?", "approver_role_id in"));
    }

    @Test
    void participantLookupRemainsAWorkflowRepositoryOwnedQuery() {
        WorkflowSqlOperations database = mock(WorkflowSqlOperations.class);
        when(database.queryForObject(anyString(), eq(Long.class), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(1L);
        JdbcWorkflowRepository repository = repository(database);

        boolean participant = repository.hasParticipant(
                9001L,
                new WorkflowRepository.TaskAssignment(1001L, "user-uuid-1001", Set.of(3001L))
        );

        assertThat(participant).isTrue();
        verify(database).queryForObject(
                org.mockito.ArgumentMatchers.contains("i.applicant_user_id = ? and i.applicant_user_uuid = ?"),
                eq(Long.class),
                org.mockito.ArgumentMatchers.any(Object[].class)
        );
    }

    private JdbcWorkflowRepository repository(WorkflowSqlOperations database) {
        return new JdbcWorkflowRepository(database, new ObjectMapper());
    }

    private WorkflowDTO.DefinitionSaveRequest definition() {
        WorkflowDTO.DefinitionSaveRequest definition = new WorkflowDTO.DefinitionSaveRequest();
        definition.setName("Expert approval");
        WorkflowDTO.NodeRequest start = new WorkflowDTO.NodeRequest();
        start.setNodeKey("start");
        start.setNodeType("START");
        start.setName("Start");
        WorkflowDTO.NodeRequest end = new WorkflowDTO.NodeRequest();
        end.setNodeKey("end");
        end.setNodeType("END");
        end.setName("End");
        definition.setNodes(List.of(start, end));
        WorkflowDTO.EdgeRequest edge = new WorkflowDTO.EdgeRequest();
        edge.setEdgeKey("start-end");
        edge.setSourceNodeKey("start");
        edge.setTargetNodeKey("end");
        definition.setEdges(List.of(edge));
        return definition;
    }
}
