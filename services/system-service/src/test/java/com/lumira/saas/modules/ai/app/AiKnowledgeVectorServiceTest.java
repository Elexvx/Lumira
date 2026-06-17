package com.lumira.saas.modules.ai.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiKnowledgeVectorServiceTest {

    @Test
    void project_shouldCreateStableLocalEmbeddingProjection() {
        AiKnowledgeVectorService service = new AiKnowledgeVectorService(new LocalHashingAiEmbeddingModel());

        AiKnowledgeVectorService.VectorProjection first = service.project("合同审批 需要 法务");
        AiKnowledgeVectorService.VectorProjection second = service.project("合同审批 需要 法务");

        assertThat(first.model()).isEqualTo(LocalHashingAiEmbeddingModel.MODEL_NAME);
        assertThat(first.dimensions()).isEqualTo(LocalHashingAiEmbeddingModel.DIMENSIONS);
        assertThat(first.vectorJson()).isEqualTo(second.vectorJson());
        assertThat(first.vectorJson()).startsWith("[");
    }

    @Test
    void score_shouldPreferSemanticallyCloserProjection() {
        AiKnowledgeVectorService service = new AiKnowledgeVectorService(new LocalHashingAiEmbeddingModel());
        AiEmbeddingVector query = service.embedQuery("合同审批");

        double contractScore = service.score(
                query,
                service.project("合同审批需要法务确认").vectorJson(),
                "合同审批",
                "合同审批需要法务确认",
                "合同制度",
                "法务知识库"
        );
        double holidayScore = service.score(
                query,
                service.project("员工假期申请流程").vectorJson(),
                "合同审批",
                "员工假期申请流程",
                "假期制度",
                "人事知识库"
        );

        assertThat(contractScore).isGreaterThan(holidayScore);
    }

    @Test
    void top_shouldReturnOnlyTheHighestScoringCandidatesInOrder() {
        AiKnowledgeVectorService service = new AiKnowledgeVectorService(new LocalHashingAiEmbeddingModel());

        var candidates = java.util.List.of(
                candidate("a", 0.25d),
                candidate("b", 0.90d),
                candidate("c", 0.50d),
                candidate("d", 0.80d)
        );

        var top = service.top(candidates, 2);

        assertThat(top).extracting(AiKnowledgeVectorService.ScoredCandidate::score)
                .containsExactly(0.90d, 0.80d);
    }

    private AiKnowledgeVectorService.ScoredCandidate candidate(String id, double score) {
        return new AiKnowledgeVectorService.ScoredCandidate() {
            @Override
            public double score() {
                return score;
            }

            @Override
            public String toString() {
                return id;
            }
        };
    }
}
