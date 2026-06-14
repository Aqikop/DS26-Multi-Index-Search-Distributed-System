package com.example.coordinator.model;

import java.util.List;

public class NodesInfo {
    private List<String> coordinatorNodes;
    private List<String> llmNodes;
    private List<String> recipeNodes;

    public NodesInfo() {}

    public List<String> getCoordinatorNodes() { return coordinatorNodes; }
    public void setCoordinatorNodes(List<String> coordinatorNodes) { this.coordinatorNodes = coordinatorNodes; }

    public List<String> getLlmNodes() { return llmNodes; }
    public void setLlmNodes(List<String> llmNodes) { this.llmNodes = llmNodes; }

    public List<String> getRecipeNodes() { return recipeNodes; }
    public void setRecipeNodes(List<String> recipeNodes) { this.recipeNodes = recipeNodes; }
}