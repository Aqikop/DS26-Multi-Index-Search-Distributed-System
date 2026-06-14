package com.example.recipesearch.service;

import com.example.shared.model.RecipeQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// ---- warmup ---
@Component
public class WarmupService implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(WarmupService.class);
    private final RecipeSearchService searchService;

    public WarmupService(RecipeSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void run(String... args) {
        log.info("Starting Qdrant warmup...");
        try {
            RecipeQuery warmupQuery = new RecipeQuery();
            warmupQuery.setRecipeQuery("warmup");
            searchService.search(warmupQuery);
            log.info("Qdrant warmup completed successfully.");
        } catch (Exception e) {
            log.warn("Qdrant warmup warning (ignored): {}", e.getMessage());
        }
    }
}
// --- warmup ----
