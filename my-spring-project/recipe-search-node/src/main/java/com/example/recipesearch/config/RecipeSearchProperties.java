package com.example.recipesearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "recipe-search")
public record RecipeSearchProperties(
        Qdrant qdrant,
        @Min(1) @Max(1000) int candidateLimit,
        @Min(1) @Max(100) int topK,
        @Min(1) @Max(300) int queryTimeoutSeconds
) {
    public RecipeSearchProperties {
        if (qdrant == null) {
            throw new IllegalArgumentException("Qdrant configuration (recipe-search.qdrant) is missing in application.yml");
        }
        // ---- kduy fix from here ---
        // if (candidateLimit == 0) candidateLimit = 100;
        // if (topK == 0) topK = 10;
        // if (queryTimeoutSeconds == 0) queryTimeoutSeconds = 60;
        // --- to here ---
    }

    public record Qdrant(
            @NotBlank String host,
            @Min(1) @Max(65535) int port,
            boolean tls,
            String apiKey,
            @NotBlank String collection,
            String vectorName,
            @NotBlank String inferenceModel
    ) {
    }
}
