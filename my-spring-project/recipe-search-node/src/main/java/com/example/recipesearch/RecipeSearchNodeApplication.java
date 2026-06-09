package com.example.recipesearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.example.recipesearch", "com.example.shared"})
@ConfigurationPropertiesScan
public class RecipeSearchNodeApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecipeSearchNodeApplication.class, args);
    }
}
