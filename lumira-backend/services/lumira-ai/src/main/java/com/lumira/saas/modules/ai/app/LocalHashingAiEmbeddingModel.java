package com.lumira.saas.modules.ai.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalHashingAiEmbeddingModel implements AiEmbeddingModel {

    public static final String MODEL_NAME = "local-hashing-v1";
    public static final int DIMENSIONS = 64;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public AiEmbeddingVector embed(String text) {
        double[] vector = new double[DIMENSIONS];
        if (!StringUtils.hasText(text)) {
            return new AiEmbeddingVector(modelName(), vector);
        }
        for (String token : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            int bucket = bucket(token);
            vector[bucket] += 1.0d;
        }
        normalize(vector);
        return new AiEmbeddingVector(modelName(), vector);
    }

    private int bucket(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0xFF) << 8) | (digest[1] & 0xFF);
            return Math.floorMod(value, DIMENSIONS);
        } catch (NoSuchAlgorithmException exception) {
            return Math.floorMod(token.hashCode(), DIMENSIONS);
        }
    }

    private void normalize(double[] vector) {
        double sumSquares = 0.0d;
        for (double value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares <= 0.0d) {
            return;
        }
        double norm = Math.sqrt(sumSquares);
        for (int index = 0; index < vector.length; index += 1) {
            vector[index] = vector[index] / norm;
        }
    }
}
