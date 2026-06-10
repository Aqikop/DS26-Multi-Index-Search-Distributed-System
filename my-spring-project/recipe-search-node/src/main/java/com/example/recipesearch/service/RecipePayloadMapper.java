package com.example.recipesearch.service;

import com.example.shared.model.Ingredient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RecipePayloadMapper {

    public RecipeDocument toDocument(Map<String, Object> payload) {
        List<Ingredient> ingredients = ingredients(payload);
        Integer explicitIngredientCount = integer(payload, "ingredientCount", "ingredient_count", "numIngredients", "num_ingredients");

        return new RecipeDocument(
                firstString(payload, "itemName", "item_name", "name", "title"),
                payloadText(payload),
                firstString(payload, "mealType", "meal_type"),
                firstString(payload, "cuisine"),
                values(payload, "cookingMethod", "cooking_method", "cooking_methods"),
                firstString(payload, "mainProtein", "main_protein", "protein"),
                values(payload, "dietFlags", "diet_flags", "diets"),
                explicitIngredientCount != null ? explicitIngredientCount : emptyToNull(ingredients),
                integer(payload, "cookTime", "cook_time", "cookTimeMinutes", "cook_time_minutes", "estimated_cook_time_min"),
                bool(payload, "hasPicture", "has_picture", "picture"),
                ingredients,
                payload
        );
    }

    private Integer emptyToNull(List<?> values) {
        return values.isEmpty() ? null : values.size();
    }

    private String payloadText(Map<String, Object> payload) {
        Object text = payload.get("payload");
        if (text == null) {
            text = payload.get("description");
        }
        if (text == null) {
            text = payload.get("text");
        }
        return text == null ? payload.toString() : String.valueOf(text);
    }

    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Integer integer(Map<String, Object> payload, String... keys) {
        Number number = number(payload, keys);
        return number == null ? null : number.intValue();
    }

    private Number number(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number;
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private Boolean bool(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text && !text.isBlank()) {
                return Boolean.parseBoolean(text);
            }
        }
        return null;
    }

    private List<String> values(Map<String, Object> payload, String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Collection<?> collection) {
                collection.stream().filter(Objects::nonNull).map(String::valueOf).forEach(result::add);
            } else if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    private List<Double> doubleValues(Map<String, Object> payload, String... keys) {
        List<Double> result = new ArrayList<>();
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Collection<?> collection) {
                collection.stream()
                        .map(this::toDouble)
                        .filter(Objects::nonNull)
                        .forEach(result::add);
            } else {
                Double parsed = toDouble(value);
                if (parsed != null) {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    private List<Ingredient> ingredients(Map<String, Object> payload) {
        List<String> names = values(payload, "ingredients", "ingredient_names", "ingredients_list");
        List<String> units = values(payload, "ingredientUnits", "ingredient_units", "units", "units_list");
        List<Double> quantities = doubleValues(payload, "ingredientQuantities", "ingredient_quantities", "quantities", "quantities_list");

        List<Ingredient> ingredients = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            Double quantity = i < quantities.size() ? quantities.get(i) : null;
            String unit = i < units.size() ? units.get(i) : null;
            ingredients.add(new Ingredient(names.get(i), quantity, unit));
        }
        return ingredients;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
