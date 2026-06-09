package com.example.shared.model;

import java.util.List;
import java.util.Map;

public class RecipeQueryResult {
    private String itemName;
    private String payload;
    private Double score;
    private List<String> ingredients;
    private List<String> ingredientUnits;
    private List<Double> ingredientQuantities;
    private Map<String, Object> metadata;
    private StatusState state;

    public RecipeQueryResult() {}

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public List<String> getIngredientUnits() { return ingredientUnits; }
    public void setIngredientUnits(List<String> ingredientUnits) { this.ingredientUnits = ingredientUnits; }

    public List<Double> getIngredientQuantities() { return ingredientQuantities; }
    public void setIngredientQuantities(List<Double> ingredientQuantities) { this.ingredientQuantities = ingredientQuantities; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public StatusState getState() { return state; }
    public void setState(StatusState state) { this.state = state; }
}
