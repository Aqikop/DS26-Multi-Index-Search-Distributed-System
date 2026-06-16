package com.example.coordinator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dishes")
public class DishController {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String etlNodeUrl;
    private final Path dishesFile = Path.of("data", "dishes.json");
    private final Object lock = new Object();

    public DishController(ObjectMapper objectMapper, RestTemplate restTemplate, @Value("${etl.node.url:http://localhost:8082}") String etlNodeUrl) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.restTemplate = restTemplate;
        this.etlNodeUrl = etlNodeUrl;
        ensureDishesFile();
    }

    @GetMapping
    public List<Dish> dishes() {
        java.util.Map<String, RecipeRecord> map = readDishes();
        List<Dish> list = new ArrayList<>();
        for (java.util.Map.Entry<String, RecipeRecord> entry : map.entrySet()) {
            Dish dish = new Dish();
            dish.id = entry.getKey();
            dish.name = entry.getValue().title;
            dish.ingredients = entry.getValue().ingredients;
            dish.cookingMethod = entry.getValue().instructions;
            list.add(dish);
        }
        return list;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dish createDish(@RequestBody Dish request) {
        String name = clean(request.name);

        if (name.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dish name is required.");
        }

        if (request.ingredients == null || safeList(request.ingredients).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one ingredient is required.");
        }

        if (clean(request.cookingMethod).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cooking method is required.");
        }

        synchronized (lock) {
            java.util.Map<String, RecipeRecord> data = readDishes();

            Dish dish = new Dish();
            dish.id = UUID.randomUUID().toString();
            dish.name = name;
            dish.ingredients = safeList(request.ingredients);
            dish.cookingMethod = clean(request.cookingMethod);

            RecipeRecord record = new RecipeRecord();
            record.title = dish.name;
            record.ingredients = dish.ingredients;
            record.instructions = dish.cookingMethod;
            record.picture_link = null;

            data.put(dish.id, record);
            writeDishes(data);

            // Send to ETL Node
            try {
                com.example.shared.model.ETLQuery etlQuery = new com.example.shared.model.ETLQuery();
                com.example.shared.model.ETLQuery.Dish sharedDish = new com.example.shared.model.ETLQuery.Dish();
                sharedDish.setId(dish.id);
                sharedDish.setName(dish.name);
                sharedDish.setIngredients(dish.ingredients);
                sharedDish.setCookingMethod(dish.cookingMethod);
                etlQuery.setDishes(List.of(sharedDish));

                com.example.shared.model.ETLQueryResult result = restTemplate.postForObject(etlNodeUrl + "/etl/process", etlQuery, com.example.shared.model.ETLQueryResult.class);
                // TODO: Redirect result to recipe node for Qdrant upload
                System.out.println("Received ETL result with " + (result != null && result.getChunks() != null ? result.getChunks().size() : 0) + " chunks.");
                
                // Pretty print the result so you can verify the metadata
                if (result != null && result.getChunks() != null) {
                    System.out.println("--- ENRICHED METADATA START ---");
                    System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.getChunks()));
                    System.out.println("--- ENRICHED METADATA END ---");
                }
            } catch (Exception e) {
                System.err.println("Failed to process dish via ETL node: " + e.getMessage());
            }

            return dish;
        }
    }

    private void ensureDishesFile() {
        synchronized (lock) {
            if (Files.exists(dishesFile)) {
                return;
            }

            try {
                Files.createDirectories(dishesFile.getParent());
                writeDishes(new java.util.HashMap<>());
            } catch (IOException e) {
                throw new IllegalStateException("Could not create dishes file.", e);
            }
        }
    }

    private java.util.Map<String, RecipeRecord> readDishes() {
        ensureDishesFile();

        synchronized (lock) {
            try {
                String content = Files.readString(dishesFile);
                if (content.trim().isEmpty()) {
                    return new java.util.HashMap<>();
                }
                return objectMapper.readValue(content, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, RecipeRecord>>() {});
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read dishes file.");
            }
        }
    }

    private void writeDishes(java.util.Map<String, RecipeRecord> data) {
        synchronized (lock) {
            try {
                objectMapper.writeValue(dishesFile.toFile(), data);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save dishes file.");
            }
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> safeList(List<String> value) {
        if (value == null) {
            return new ArrayList<>();
        }

        return value.stream()
                .map(DishController::clean)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    public static class RecipeRecord {
        public String title;
        public List<String> ingredients = new ArrayList<>();
        public String instructions;
        public String picture_link;
    }

    public static class Dish {
        public String id;
        public String name;
        public List<String> ingredients = new ArrayList<>();
        public String cookingMethod;
    }
}