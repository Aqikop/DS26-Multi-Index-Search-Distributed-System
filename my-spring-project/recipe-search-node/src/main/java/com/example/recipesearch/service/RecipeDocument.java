package com.example.recipesearch.service;

import com.example.shared.model.Ingredient;
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
        Map<String, Object> rawPayload
) {
}
