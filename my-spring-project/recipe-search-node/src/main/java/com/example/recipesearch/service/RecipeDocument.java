package com.example.recipesearch.service;

import com.example.shared.model.Ingredient;
import com.example.shared.model.Nutrition;
import java.util.List;
import java.util.Map;

public record RecipeDocument(
        String itemName,
        String payloadText,
        String mealType,
        String cuisine,
        List<String> cookingMethods,
        String mainProtein,
        List<String> dietFlags,
        Integer ingredientCount,
        Integer cookTime,
        Boolean hasPicture,
        List<Ingredient> ingredients,
        Nutrition nutrition,
        Map<String, Object> rawPayload
) {
}
