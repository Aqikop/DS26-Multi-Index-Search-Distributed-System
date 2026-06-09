package com.example.shared.model;

public class RecipeQuery {
    private String recipeQuery;
    private RecipeFilters filters;
    private StatusState state;

    public RecipeQuery() {}

    public String getRecipeQuery() { return recipeQuery; }
    public void setRecipeQuery(String recipeQuery) { this.recipeQuery = recipeQuery; }

    public RecipeFilters getFilters() { return filters; }
    public void setFilters(RecipeFilters filters) { this.filters = filters; }

    public StatusState getState() { return state; }
    public void setState(StatusState state) { this.state = state; }
}