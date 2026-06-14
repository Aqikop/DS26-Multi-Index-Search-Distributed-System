package com.example.recipesearch.service;

import com.example.recipesearch.config.RecipeSearchProperties;
import com.example.recipesearch.repository.QdrantRecipeRepository;
import com.example.recipesearch.repository.RecipeCandidate;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecipeSearchService {
    private static final Logger log = LoggerFactory.getLogger(RecipeSearchService.class);
    private final QdrantRecipeRepository recipeRepository;
    private final RecipeRankingService rankingService;
    private final RecipeSearchProperties properties;

    public RecipeSearchService(
            QdrantRecipeRepository recipeRepository,
            RecipeRankingService rankingService,
            RecipeSearchProperties properties
    ) {
        this.recipeRepository = recipeRepository;
        this.rankingService = rankingService;
        this.properties = properties;
    }

    public List<RecipeQueryResult> search(RecipeQuery query) {
        if (query == null || query.getRecipeQuery() == null || query.getRecipeQuery().isBlank()) {
            log.warn("Search called with null or blank query");
            // ---- kduy fix from here ---
            // return List.of();
            throw new IllegalArgumentException("Blank query");
            // --- to here ---
        }

        long startTime = System.currentTimeMillis();
        List<RecipeCandidate> candidates = recipeRepository.findCandidates(query.getRecipeQuery());
        List<RecipeQueryResult> results = rankingService.rank(query, candidates, properties.topK());
        log.info("Search completed in {}ms — query='{}', candidates={}, results={}",
                System.currentTimeMillis() - startTime, query.getRecipeQuery(), candidates.size(), results.size());
        return results;
    }
}
