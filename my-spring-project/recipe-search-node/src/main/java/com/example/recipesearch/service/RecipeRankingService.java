package com.example.recipesearch.service;

import com.example.recipesearch.repository.RecipeCandidate;
import com.example.shared.model.RecipeFilters;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RecipeRankingService {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    public List<RecipeQueryResult> rank(RecipeQuery query, List<RecipeCandidate> candidates, int topK) {
        RecipeFilters filters = query.getFilters();
        Set<String> queryTerms = tokenize(query.getRecipeQuery());

        return candidates.stream()
                .filter(candidate -> matchesFilters(candidate.payload(), filters))
                .map(candidate -> toRankedResult(candidate, filters, queryTerms))
                .sorted(Comparator.comparing(RecipeQueryResult::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(topK)
                .toList();
    }

    private RecipeQueryResult toRankedResult(
            RecipeCandidate candidate,
            RecipeFilters filters,
            Set<String> queryTerms
    ) {
        Map<String, Object> payload = candidate.payload();
        double qdrantScore = normalize(candidate.qdrantScore());
        double ingredientOverlap = ingredientOverlap(queryTerms, payload);
        double cookTimePreference = cookTimePreference(filters, payload);
        double proteinRelevance = proteinRelevance(filters, payload, queryTerms);
        int matchedFilters = countMatchedFilters(payload, filters);

        double finalScore =
                (qdrantScore * 0.55)
                        + (ingredientOverlap * 0.20)
                        + (cookTimePreference * 0.15)
                        + (proteinRelevance * 0.10);

        RecipeQueryResult result = new RecipeQueryResult();
        result.setItemName(firstString(payload, "itemName", "item_name", "name", "title"));
        result.setPayload(stringPayload(payload));
        result.setScore(round(finalScore));
        result.setIngredients(values(payload, "ingredients", "ingredient_names"));
        result.setIngredientUnits(values(payload, "ingredientUnits", "ingredient_units", "units"));
        result.setIngredientQuantities(doubleValues(payload, "ingredientQuantities", "ingredient_quantities", "quantities"));
        result.setMetadata(metadata(candidate, payload, matchedFilters));
        return result;
    }

    public boolean matchesFilters(Map<String, Object> payload, RecipeFilters filters) {
        if (filters == null) {
            return true;
        }
        return equalsIfPresent(filters.getMealType(), firstString(payload, "mealType", "meal_type"))
                && equalsIfPresent(filters.getCuisine(), firstString(payload, "cuisine"))
                && anyIfPresent(filters.getCookingMethod(), values(payload, "cookingMethod", "cooking_method", "cooking_methods"))
                && equalsIfPresent(filters.getMainProtein(), firstString(payload, "mainProtein", "main_protein", "protein"))
                && allIfPresent(filters.getDietFlags(), values(payload, "dietFlags", "diet_flags", "diets"))
                && lessOrEqualIfPresent(sizeFromPayload(payload), filters.getMaxIngredients())
                && lessOrEqualIfPresent(number(payload, "cookTime", "cook_time", "cookTimeMinutes", "cook_time_minutes"), filters.getMaxCookTime())
                && equalsIfPresent(filters.getHasPicture(), bool(payload, "hasPicture", "has_picture", "picture"));
    }

    private Map<String, Object> metadata(RecipeCandidate candidate, Map<String, Object> payload, int matchedFilters) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("qdrantScore", round(candidate.qdrantScore()));
        metadata.put("cookTime", number(payload, "cookTime", "cook_time", "cookTimeMinutes", "cook_time_minutes"));
        metadata.put("mainProtein", firstString(payload, "mainProtein", "main_protein", "protein"));
        metadata.put("ingredientCount", sizeFromPayload(payload));
        metadata.put("matchedFilters", matchedFilters);
        metadata.put("qdrantPointId", candidate.id());
        return metadata;
    }

    private int countMatchedFilters(Map<String, Object> payload, RecipeFilters filters) {
        if (filters == null) {
            return 0;
        }
        int matched = 0;
        if (filters.getMealType() != null && equalsIfPresent(filters.getMealType(), firstString(payload, "mealType", "meal_type"))) matched++;
        if (filters.getCuisine() != null && equalsIfPresent(filters.getCuisine(), firstString(payload, "cuisine"))) matched++;
        if (filters.getCookingMethod() != null && anyIfPresent(filters.getCookingMethod(), values(payload, "cookingMethod", "cooking_method", "cooking_methods"))) matched++;
        if (filters.getMainProtein() != null && equalsIfPresent(filters.getMainProtein(), firstString(payload, "mainProtein", "main_protein", "protein"))) matched++;
        if (filters.getDietFlags() != null && allIfPresent(filters.getDietFlags(), values(payload, "dietFlags", "diet_flags", "diets"))) matched++;
        if (filters.getMaxIngredients() != null && lessOrEqualIfPresent(sizeFromPayload(payload), filters.getMaxIngredients())) matched++;
        if (filters.getMaxCookTime() != null && lessOrEqualIfPresent(number(payload, "cookTime", "cook_time", "cookTimeMinutes", "cook_time_minutes"), filters.getMaxCookTime())) matched++;
        if (filters.getHasPicture() != null && equalsIfPresent(filters.getHasPicture(), bool(payload, "hasPicture", "has_picture", "picture"))) matched++;
        return matched;
    }

    private double ingredientOverlap(Set<String> queryTerms, Map<String, Object> payload) {
        Set<String> ingredientTerms = tokenize(values(payload, "ingredients", "ingredient_names"));
        if (queryTerms.isEmpty() || ingredientTerms.isEmpty()) {
            return 0.0;
        }
        long hits = ingredientTerms.stream().filter(queryTerms::contains).count();
        return Math.min(1.0, hits / (double) Math.min(queryTerms.size(), ingredientTerms.size()));
    }

    private double cookTimePreference(RecipeFilters filters, Map<String, Object> payload) {
        Number cookTime = number(payload, "cookTime", "cook_time", "cookTimeMinutes", "cook_time_minutes");
        if (cookTime == null) {
            return 0.5;
        }
        if (filters == null || filters.getMaxCookTime() == null || filters.getMaxCookTime() <= 0) {
            return 1.0;
        }
        double ratio = cookTime.doubleValue() / filters.getMaxCookTime();
        return Math.max(0.0, Math.min(1.0, 1.2 - ratio));
    }

    private double proteinRelevance(RecipeFilters filters, Map<String, Object> payload, Set<String> queryTerms) {
        String protein = firstString(payload, "mainProtein", "main_protein", "protein");
        if (protein == null || protein.isBlank()) {
            return 0.0;
        }
        String normalizedProtein = normalizeText(protein);
        if (filters != null && filters.getMainProtein() != null
                && normalizeText(filters.getMainProtein()).equals(normalizedProtein)) {
            return 1.0;
        }
        return queryTerms.contains(normalizedProtein) ? 0.75 : 0.0;
    }

    private Number sizeFromPayload(Map<String, Object> payload) {
        Number explicit = number(payload, "ingredientCount", "ingredient_count", "numIngredients", "num_ingredients");
        if (explicit != null) {
            return explicit;
        }
        List<String> ingredients = values(payload, "ingredients", "ingredient_names");
        return ingredients.isEmpty() ? null : ingredients.size();
    }

    private boolean equalsIfPresent(String expected, String actual) {
        return expected == null || normalizeText(expected).equals(normalizeText(actual));
    }

    private boolean equalsIfPresent(Boolean expected, Boolean actual) {
        return expected == null || Objects.equals(expected, actual);
    }

    private boolean lessOrEqualIfPresent(Number actual, Integer max) {
        return max == null || (actual != null && actual.doubleValue() <= max);
    }

    private boolean anyIfPresent(Collection<String> expected, Collection<String> actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        Set<String> normalizedActual = normalizeAll(actual);
        return expected.stream().map(this::normalizeText).anyMatch(normalizedActual::contains);
    }

    private boolean allIfPresent(Collection<String> expected, Collection<String> actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        Set<String> normalizedActual = normalizeAll(actual);
        return expected.stream().map(this::normalizeText).allMatch(normalizedActual::contains);
    }

    private Set<String> normalizeAll(Collection<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(normalizeText(value));
        }
        return normalized;
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return tokenize(List.of(text));
    }

    private Set<String> tokenize(Collection<String> texts) {
        Set<String> terms = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            for (String token : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
                if (token.length() > 2) {
                    terms.add(token);
                }
            }
        }
        return terms;
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

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private double normalize(double score) {
        if (score >= 0.0 && score <= 1.0) {
            return score;
        }
        return 1.0 / (1.0 + Math.exp(-score));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String stringPayload(Map<String, Object> payload) {
        Object text = payload.get("payload");
        if (text == null) {
            text = payload.get("description");
        }
        if (text == null) {
            text = payload.get("text");
        }
        return text == null ? payload.toString() : String.valueOf(text);
    }
}
