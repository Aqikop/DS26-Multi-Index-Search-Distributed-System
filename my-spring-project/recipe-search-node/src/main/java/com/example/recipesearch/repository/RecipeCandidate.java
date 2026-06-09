package com.example.recipesearch.repository;

import java.util.Map;

public record RecipeCandidate(String id, double qdrantScore, Map<String, Object> payload) {}
