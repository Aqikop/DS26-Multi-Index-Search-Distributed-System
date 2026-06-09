package com.example.shared.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserIntent {
    private List<String> collections;
    private boolean estimateNutrition;
    private String recipeQuery;
    private String nutritionQuery;
    private RecipeFilters recipeFilters;
    private NutritionFilters nutritionFilters;

    public UserIntent() {}

    public List<String> getCollections() { return collections; }
    public void setCollections(List<String> collections) { this.collections = collections; }

    public boolean isEstimateNutrition() { return estimateNutrition; }
    public void setEstimateNutrition(boolean estimateNutrition) { this.estimateNutrition = estimateNutrition; }

    public String getRecipeQuery() { return recipeQuery; }
    public void setRecipeQuery(String recipeQuery) { this.recipeQuery = recipeQuery; }

    public String getNutritionQuery() { return nutritionQuery; }
    public void setNutritionQuery(String nutritionQuery) { this.nutritionQuery = nutritionQuery; }

    public RecipeFilters getRecipeFilters() { return recipeFilters; }
    public void setRecipeFilters(RecipeFilters recipeFilters) { this.recipeFilters = recipeFilters; }

    public NutritionFilters getNutritionFilters() { return nutritionFilters; }
    public void setNutritionFilters(NutritionFilters nutritionFilters) { this.nutritionFilters = nutritionFilters; }
}