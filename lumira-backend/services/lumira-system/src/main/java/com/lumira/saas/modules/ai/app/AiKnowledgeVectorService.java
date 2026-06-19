package com.lumira.saas.modules.ai.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiKnowledgeVectorService {

    private final AiEmbeddingModel embeddingModel;

    public AiKnowledgeVectorService(AiEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public VectorProjection project(String text) {
        AiEmbeddingVector vector = embeddingModel.embed(text);
        return new VectorProjection(vector.model(), vector.dimensions(), serialize(vector.values()));
    }

    public AiEmbeddingVector embedQuery(String query) {
        return embeddingModel.embed(query);
    }

    public double score(AiEmbeddingVector queryVector, String vectorJson, String query, String content, String title, String knowledgeBaseName) {
        double vectorScore = cosine(queryVector.values(), parse(vectorJson));
        double lexicalScore = lexicalScore(query, content, title, knowledgeBaseName);
        return (0.82d * vectorScore) + (0.18d * lexicalScore);
    }

    public <T extends ScoredCandidate> List<T> top(List<T> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, limit);
        if (candidates.size() <= normalizedLimit) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                    .toList();
        }

        PriorityQueue<RankedCandidate<T>> heap = new PriorityQueue<>(Comparator.comparingDouble(RankedCandidate::score));
        for (T candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            RankedCandidate<T> rankedCandidate = new RankedCandidate<>(candidate, candidate.score());
            if (heap.size() < normalizedLimit) {
                heap.offer(rankedCandidate);
                continue;
            }
            RankedCandidate<T> smallest = heap.peek();
            if (smallest != null && rankedCandidate.score() > smallest.score()) {
                heap.poll();
                heap.offer(rankedCandidate);
            }
        }

        ArrayList<RankedCandidate<T>> rankedCandidates = new ArrayList<>(heap);
        rankedCandidates.sort(Comparator.comparingDouble(RankedCandidate<T>::score).reversed());
        ArrayList<T> selected = new ArrayList<>(rankedCandidates.size());
        for (RankedCandidate<T> rankedCandidate : rankedCandidates) {
            selected.add(rankedCandidate.candidate());
        }
        return List.copyOf(selected);
    }

    private String serialize(double[] values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.length; index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Double.toString(values[index]));
        }
        return builder.append(']').toString();
    }

    private double[] parse(String vectorJson) {
        if (!StringUtils.hasText(vectorJson)) {
            return new double[0];
        }
        String trimmed = vectorJson.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return new double[0];
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (!StringUtils.hasText(body)) {
            return new double[0];
        }
        String[] parts = body.split(",");
        List<Double> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                values.add(Double.parseDouble(part.trim()));
            } catch (NumberFormatException exception) {
                return new double[0];
            }
        }
        double[] parsed = new double[values.size()];
        for (int index = 0; index < values.size(); index += 1) {
            parsed[index] = values.get(index);
        }
        return parsed;
    }

    private double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftSquares = 0.0d;
        double rightSquares = 0.0d;
        for (int index = 0; index < left.length; index += 1) {
            dot += left[index] * right[index];
            leftSquares += left[index] * left[index];
            rightSquares += right[index] * right[index];
        }
        if (leftSquares <= 0.0d || rightSquares <= 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftSquares) * Math.sqrt(rightSquares));
    }

    private double lexicalScore(String query, String content, String title, String knowledgeBaseName) {
        if (!StringUtils.hasText(query)) {
            return 0.0d;
        }
        String haystack = (safe(content) + " " + safe(title) + " " + safe(knowledgeBaseName)).toLowerCase(Locale.ROOT);
        String[] tokens = query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        int matched = 0;
        int total = 0;
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            total++;
            if (haystack.contains(token)) {
                matched++;
            }
        }
        return total == 0 ? 0.0d : (double) matched / total;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record VectorProjection(
            String model,
            int dimensions,
            String vectorJson
    ) {
    }

    public interface ScoredCandidate {
        double score();
    }

    private record RankedCandidate<T extends ScoredCandidate>(T candidate, double score) {
    }
}
