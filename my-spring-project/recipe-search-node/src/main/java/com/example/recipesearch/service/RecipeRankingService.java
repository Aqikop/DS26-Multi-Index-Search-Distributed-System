package com.example.recipesearch.service;

import com.example.recipesearch.repository.RecipeCandidate;
import com.example.shared.model.Ingredient;
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

    private final RecipePayloadMapper payloadMapper;

    public RecipeRankingService(RecipePayloadMapper payloadMapper) {
        this.payloadMapper = payloadMapper;
    }

    public List<RecipeQueryResult> rank(RecipeQuery query, List<RecipeCandidate> candidates, int topK) {
        RecipeFilters filters = query.getFilters();
        Set<String> queryTerms = tokenize(query.getRecipeQuery());

        return candidates.stream()
                .map(candidate -> new RankedCandidate(candidate, payloadMapper.toDocument(candidate.payload())))
                .filter(candidate -> matchesFilters(candidate.document(), filters))
                .map(candidate -> toRankedResult(candidate, filters, queryTerms))
                .sorted(Comparator.comparing(RecipeQueryResult::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(topK)
                .toList();
    }

    private RecipeQueryResult toRankedResult(
            RankedCandidate rankedCandidate,
            RecipeFilters filters,
            Set<String> queryTerms
    ) {
        RecipeCandidate candidate = rankedCandidate.candidate();
        RecipeDocument document = rankedCandidate.document();
        double qdrantScore = normalize(candidate.qdrantScore());
        double ingredientOverlap = ingredientOverlap(queryTerms, document);
        double cookTimePreference = cookTimePreference(filters, document);
        double proteinRelevance = proteinRelevance(filters, document, queryTerms);
        int matchedFilters = countMatchedFilters(document, filters);

        double finalScore =
                (qdrantScore * 0.55)
                        + (ingredientOverlap * 0.20)
                        + (cookTimePreference * 0.15)
                        + (proteinRelevance * 0.10);

        RecipeQueryResult result = new RecipeQueryResult();
        result.setItemName(document.itemName());
        result.setPayload(document.payloadText());
        result.setScore(round(finalScore));
        result.setIngredients(document.ingredients());
        result.setMissingIngredients(calculateMissingIngredients(document.ingredients(), filters));
        result.setMetadata(metadata(candidate, document, matchedFilters));
        return result;
    }

    private List<Ingredient> calculateMissingIngredients(List<Ingredient> recipeIngredients, RecipeFilters filters) {
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return List.of();
        if (filters == null || filters.getIngredients() == null || filters.getIngredients().isEmpty()) return recipeIngredients;

        List<Ingredient> userIngredients = filters.getIngredients();
        Map<String, Double> userInventory = new LinkedHashMap<>();
        
        for (Ingredient ui : userIngredients) {
            if (ui.getName() == null) continue;
            String normName = normalizeText(ui.getName());
            userInventory.put(normName, ui.getQuantity());
        }

        List<Ingredient> missing = new ArrayList<>();
        for (Ingredient ri : recipeIngredients) {
            if (ri.getName() == null) continue;
            String normRecipeName = normalizeText(ri.getName());
            
            boolean found = false;
            // Fuzzy match: check if recipe ingredient name is contained in user ingredient name or vice versa
            for (Map.Entry<String, Double> entry : userInventory.entrySet()) {
                if (normRecipeName.contains(entry.getKey()) || entry.getKey().contains(normRecipeName)) {
                    found = true;
                    // Check quantity if available
                    if (ri.getQuantity() != null && entry.getValue() != null) {
                        double missingQty = ri.getQuantity() - entry.getValue();
                        if (missingQty > 0) {
                            missing.add(new Ingredient(ri.getName(), missingQty, ri.getUnit()));
                        }
                    }
                    break;
                }
            }
            if (!found) {
                missing.add(ri);
            }
        }
        return missing;
    }

    public boolean matchesFilters(RecipeDocument document, RecipeFilters filters) {
        if (filters == null) {
            return true;
        }
        return equalsIfPresent(filters.getMealType(), document.mealType())
                && equalsIfPresent(filters.getCuisine(), document.cuisine())
                && anyIfPresent(filters.getCookingMethod(), document.cookingMethods())
                && equalsIfPresent(filters.getMainProtein(), document.mainProtein())
                && allIfPresent(filters.getDietFlags(), document.dietFlags())
                && lessOrEqualIfPresent(document.ingredientCount(), filters.getMaxIngredients())
                && lessOrEqualIfPresent(document.cookTime(), filters.getMaxCookTime())
                && equalsIfPresent(filters.getHasPicture(), document.hasPicture());
    }

    private Map<String, Object> metadata(RecipeCandidate candidate, RecipeDocument document, int matchedFilters) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("qdrantScore", round(candidate.qdrantScore()));
        metadata.put("cookTime", document.cookTime());
        metadata.put("mainProtein", document.mainProtein());
        metadata.put("ingredientCount", document.ingredientCount());
        metadata.put("matchedFilters", matchedFilters);
        metadata.put("qdrantPointId", candidate.id());
        return metadata;
    }

    private int countMatchedFilters(RecipeDocument document, RecipeFilters filters) {
        if (filters == null) {
            return 0;
        }
        int matched = 0;
        if (filters.getMealType() != null && equalsIfPresent(filters.getMealType(), document.mealType())) matched++;
        if (filters.getCuisine() != null && equalsIfPresent(filters.getCuisine(), document.cuisine())) matched++;
        if (filters.getCookingMethod() != null && anyIfPresent(filters.getCookingMethod(), document.cookingMethods())) matched++;
        if (filters.getMainProtein() != null && equalsIfPresent(filters.getMainProtein(), document.mainProtein())) matched++;
        if (filters.getDietFlags() != null && allIfPresent(filters.getDietFlags(), document.dietFlags())) matched++;
        if (filters.getMaxIngredients() != null && lessOrEqualIfPresent(document.ingredientCount(), filters.getMaxIngredients())) matched++;
        if (filters.getMaxCookTime() != null && lessOrEqualIfPresent(document.cookTime(), filters.getMaxCookTime())) matched++;
        if (filters.getHasPicture() != null && equalsIfPresent(filters.getHasPicture(), document.hasPicture())) matched++;
        return matched;
    }

    private double ingredientOverlap(Set<String> queryTerms, RecipeDocument document) {
        Set<String> ingredientTerms = tokenize(ingredientNames(document.ingredients()));
        if (queryTerms.isEmpty() || ingredientTerms.isEmpty()) {
            return 0.0;
        }
        long hits = ingredientTerms.stream().filter(queryTerms::contains).count();
        return Math.min(1.0, hits / (double) Math.min(queryTerms.size(), ingredientTerms.size()));
    }

    private List<String> ingredientNames(List<Ingredient> ingredients) {
        return ingredients.stream()
                .map(Ingredient::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    private double cookTimePreference(RecipeFilters filters, RecipeDocument document) {
        Integer cookTime = document.cookTime();
        if (cookTime == null) {
            return 0.5;
        }
        if (filters == null || filters.getMaxCookTime() == null || filters.getMaxCookTime() <= 0) {
            return 1.0;
        }
        double ratio = cookTime.doubleValue() / filters.getMaxCookTime();
        return Math.max(0.0, Math.min(1.0, 1.2 - ratio));
    }

    private double proteinRelevance(RecipeFilters filters, RecipeDocument document, Set<String> queryTerms) {
        String protein = document.mainProtein();
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

    private record RankedCandidate(RecipeCandidate candidate, RecipeDocument document) {
    }
}
