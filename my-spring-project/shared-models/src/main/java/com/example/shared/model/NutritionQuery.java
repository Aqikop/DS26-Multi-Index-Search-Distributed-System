package com.example.shared.model;

public class NutritionQuery {
    private String nutritionQuery;
    private NutritionFilters filters;
    private StatusState state;

    public NutritionQuery() {}

    public String getNutritionQuery() {
        return nutritionQuery;
    }

    public void setNutritionQuery(String nutritionQuery) {
        this.nutritionQuery = nutritionQuery;
    }

    public NutritionFilters getFilters() {
        return filters;
    }

    public void setFilters(NutritionFilters filters) {
        this.filters = filters;
    }

    public StatusState getState() {
        return state;
    }

    public void setState(StatusState state) {
        this.state = state;
    }
}