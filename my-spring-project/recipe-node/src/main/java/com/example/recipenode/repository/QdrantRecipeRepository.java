package com.example.recipenode.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.example.recipenode.config.RecipeNodeProperties;
import com.example.recipenode.model.RecipeCandidate;

import io.qdrant.client.QdrantClient;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;

@Repository
public class QdrantRecipeRepository {
    private static final Logger log = LoggerFactory.getLogger(QdrantRecipeRepository.class);

    private final QdrantClient qdrantClient;
    private final RecipeNodeProperties properties;

    public QdrantRecipeRepository(
            QdrantClient qdrantClient,
            RecipeNodeProperties properties
    ) {
        this.qdrantClient = qdrantClient;
        this.properties = properties;
    }

    public List<RecipeCandidate> findCandidates(String semanticText) {
        if (semanticText == null || semanticText.isBlank()) {
            return List.of();
        }

        RecipeNodeProperties.Qdrant qdrant = properties.qdrant();
        QueryPoints.Builder request = QueryPoints.newBuilder()
                .setCollectionName(qdrant.collection())
                .setQuery(nearest(Document.newBuilder()
                        .setText(semanticText)
                        .setModel(qdrant.inferenceModel())
                        .build()))
                .setLimit(properties.candidateLimit())
                .setWithPayload(enable(true));

        if (qdrant.vectorName() != null && !qdrant.vectorName().isBlank()) {
            request.setUsing(qdrant.vectorName());
        }

        try {
            List<ScoredPoint> points = qdrantClient.queryAsync(request.build()).get(properties.queryTimeoutSeconds(), TimeUnit.SECONDS);
            List<RecipeCandidate> candidates = new ArrayList<>(points.size());
            for (ScoredPoint point : points) {
                candidates.add(new RecipeCandidate(
                        point.getId().hasUuid() ? point.getId().getUuid() : String.valueOf(point.getId().getNum()),
                        point.getScore(),
                        convertPayload(point.getPayloadMap())
                ));
            }
            return candidates;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecipeRepositoryException("Interrupted while querying Qdrant recipes collection", e);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Qdrant recipe search failed or timed out for collection {}", qdrant.collection(), e);
            throw new RecipeRepositoryException("Unable to query Qdrant recipes collection", e);
        }
    }

    private Map<String, Object> convertPayload(Map<String, JsonWithInt.Value> payload) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : payload.entrySet()) {
            converted.put(entry.getKey(), convertValue(entry.getValue()));
        }
        return converted;
    }

    private Object convertValue(JsonWithInt.Value value) {
        if (value == null) {
            return null;
        }
        return switch (value.getKindCase()) {
            case NULL_VALUE, KIND_NOT_SET -> null;
            case DOUBLE_VALUE -> value.getDoubleValue();
            case INTEGER_VALUE -> value.getIntegerValue();
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> convertPayload(value.getStructValue().getFieldsMap());
            case LIST_VALUE -> value.getListValue().getValuesList().stream()
                    .map(this::convertValue)
                    .toList();
        };
    }
}
