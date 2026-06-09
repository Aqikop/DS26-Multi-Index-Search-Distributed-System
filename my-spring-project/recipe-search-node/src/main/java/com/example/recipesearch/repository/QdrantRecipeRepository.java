package com.example.recipesearch.repository;

import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

import com.example.recipesearch.config.RecipeSearchProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class QdrantRecipeRepository {
    private static final Logger log = LoggerFactory.getLogger(QdrantRecipeRepository.class);

    private final QdrantClient qdrantClient;
    private final RecipeSearchProperties properties;

    public QdrantRecipeRepository(
            QdrantClient qdrantClient,
            RecipeSearchProperties properties
    ) {
        this.qdrantClient = qdrantClient;
        this.properties = properties;
    }

    public List<RecipeCandidate> findCandidates(String semanticText) {
        if (semanticText == null || semanticText.isBlank()) {
            return List.of();
        }

        RecipeSearchProperties.Qdrant qdrant = properties.qdrant();
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
            List<ScoredPoint> points = qdrantClient.queryAsync(request.build()).get();
            List<RecipeCandidate> candidates = new ArrayList<>(points.size());
            for (ScoredPoint point : points) {
                candidates.add(new RecipeCandidate(
                        point.getId().toString(),
                        point.getScore(),
                        convertPayload(point.getPayloadMap())
                ));
            }
            return candidates;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecipeSearchRepositoryException("Interrupted while querying Qdrant recipes collection", e);
        } catch (ExecutionException e) {
            log.warn("Qdrant recipe search failed for collection {}", qdrant.collection(), e);
            throw new RecipeSearchRepositoryException("Unable to query Qdrant recipes collection", e);
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
                    .filter(Objects::nonNull)
                    .toList();
        };
    }
}
