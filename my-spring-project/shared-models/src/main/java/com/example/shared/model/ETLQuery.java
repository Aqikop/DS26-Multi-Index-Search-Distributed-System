package com.example.shared.model;

import java.util.List;

public class ETLQuery {
    private List<Dish> dishes;

    public List<Dish> getDishes() {
        return dishes;
    }

    public void setDishes(List<Dish> dishes) {
        this.dishes = dishes;
    }

    public static class Dish {
        private String id;
        private String name;
        private List<String> ingredients;
        private String cookingMethod;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getIngredients() {
            return ingredients;
        }

        public void setIngredients(List<String> ingredients) {
            this.ingredients = ingredients;
        }

        public String getCookingMethod() {
            return cookingMethod;
        }

        public void setCookingMethod(String cookingMethod) {
            this.cookingMethod = cookingMethod;
        }
    }
}
