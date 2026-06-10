package com.example.recipesearch.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.recipesearch.repository.RecipeCandidate;
import com.example.shared.model.RecipeFilters;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeRankingServiceTest {
    private final RecipeRankingService rankingService = new RecipeRankingService(new RecipePayloadMapper());

    @Test
    void filtersAndRanksCandidatesWithApplicationScore() {
        RecipeQuery query = new RecipeQuery();
        query.setRecipeQuery("high protein low carb chicken dinner");

        RecipeFilters filters = new RecipeFilters();
        filters.setMealType("main_course");
        filters.setMainProtein("chicken");
        filters.setMaxCookTime(30);
        filters.setHasPicture(true);
        query.setFilters(filters);

        RecipeCandidate fastChicken = new RecipeCandidate("1", 0.82, Map.of(
                "name", "Fast Chicken Bowl",
                "meal_type", "main_course",
                "main_protein", "chicken",
                "cook_time", 25,
                "has_picture", true,
                "ingredients", List.of("chicken breast", "broccoli", "low carb sauce"),
                "ingredient_units", List.of("g", "g", "tbsp"),
                "ingredient_quantities", List.of(200.0, 120.0, 1.0)
        ));
        RecipeCandidate slowChicken = new RecipeCandidate("2", 0.95, Map.of(
                "name", "Slow Chicken Dinner",
                "meal_type", "main_course",
                "main_protein", "chicken",
                "cook_time", 55,
                "has_picture", true,
                "ingredients", List.of("chicken", "rice")
        ));
        RecipeCandidate fish = new RecipeCandidate("3", 0.99, Map.of(
                "name", "Fish Dinner",
                "meal_type", "main_course",
                "main_protein", "fish",
                "cook_time", 20,
                "has_picture", true
        ));

        List<RecipeQueryResult> results = rankingService.rank(query, List.of(slowChicken, fish, fastChicken), 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getItemName()).isEqualTo("Fast Chicken Bowl");
        assertThat(results.get(0).getIngredients())
                .extracting("name")
                .containsExactly("chicken breast", "broccoli", "low carb sauce");
        assertThat(results.get(0).getIngredients())
                .extracting("unit")
                .containsExactly("g", "g", "tbsp");
        assertThat(results.get(0).getIngredients())
                .extracting("quantity")
                .containsExactly(200.0, 120.0, 1.0);
        assertThat(results.get(0).getMetadata())
                .containsEntry("qdrantScore", 0.82)
                .containsEntry("cookTime", 25)
                .containsEntry("mainProtein", "chicken")
                .containsEntry("ingredientCount", 3)
                .containsEntry("matchedFilters", 4);
    }
}
