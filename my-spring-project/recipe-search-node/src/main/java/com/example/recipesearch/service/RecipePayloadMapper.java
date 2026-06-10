package com.example.recipesearch.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.shared.model.Ingredient;

@Component
public class RecipePayloadMapper {

    public RecipeDocument toDocument(Map<String, Object> payload) {
        List<Ingredient> ingredients = ingredients(payload);
        Integer explicitIngredientCount = getInteger(payload, "ingredient_count");

        return new RecipeDocument(
                getString(payload, "title"),
                getString(payload, "text"),
                getString(payload, "meal_type"),
                getString(payload, "cuisine"),
                getStringList(payload, "cooking_method"),
                getString(payload, "main_protein"),
                getStringList(payload, "diet_flags"),
                explicitIngredientCount != null ? explicitIngredientCount : (ingredients.isEmpty() ? null : ingredients.size()),
                getInteger(payload, "estimated_cook_time_min"),
                getBoolean(payload, "has_picture"),
                ingredients,
                payload
        );
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Integer getInteger(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.valueOf(text).intValue();
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Boolean getBoolean(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.valueOf(text);
        }
        return null;
    }

    private List<String> getStringList(Map<String, Object> payload, String key) {
        List<String> result = new ArrayList<>();
        Object value = payload.get(key);
        if (value instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).map(String::valueOf).forEach(result::add);
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private List<Double> getDoubleList(Map<String, Object> payload, String key) {
        List<Double> result = new ArrayList<>();
        Object value = payload.get(key);
        if (value instanceof Collection<?> collection) {
            collection.stream()
                    .map(this::toDouble)
                    .filter(Objects::nonNull)
                    .forEach(result::add);
        } else {
            Double parsed = toDouble(value);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private List<Ingredient> ingredients(Map<String, Object> payload) {
        List<String> names = getStringList(payload, "ingredients_list");
        List<String> units = getStringList(payload, "units_list");
        List<Double> quantities = getDoubleList(payload, "quantities_list");

        List<Ingredient> ingredients = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            Double quantity = i < quantities.size() ? quantities.get(i) : null;
            String unit = i < units.size() ? units.get(i) : null;
            ingredients.add(new Ingredient(names.get(i), quantity, unit));
        }
        return ingredients;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Double.valueOf(text); } 
            catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
