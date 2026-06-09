package com.example.recipesearch.controller;

import com.example.recipesearch.service.RecipeSearchService;
import com.example.shared.model.RecipeQuery;
import com.example.shared.model.RecipeQueryResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recipes")
public class RecipeSearchController {
    private final RecipeSearchService recipeSearchService;

    public RecipeSearchController(RecipeSearchService recipeSearchService) {
        this.recipeSearchService = recipeSearchService;
    }

    @PostMapping("/search")
    ResponseEntity<List<RecipeQueryResult>> search(@Valid @RequestBody RecipeQuery query) {
        return ResponseEntity.ok(recipeSearchService.search(query));
    }
}
