package com.example.recipesearch.service;

import com.example.recipesearch.config.RecipeSearchProperties;
import com.example.recipesearch.repository.QdrantRecipeRepository;
import com.example.recipesearch.repository.RecipeCandidate;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecipeSearchService {
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
            return List.of();
        }

        List<RecipeCandidate> candidates = recipeRepository.findCandidates(query.getRecipeQuery());
        return rankingService.rank(query, candidates, properties.topK());
    }
}
