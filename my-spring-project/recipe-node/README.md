# Recipe Node

`recipe-node` is a Spring Boot microservice that acts as the "recipe search server" within the distributed system. It receives semantic queries (parsed by the LLM), searches the **Qdrant** vector database, and applies a multi-factor ranking algorithm to return the most relevant recipes.

## 🏗 Data Flow Architecture

```text
POST /recipes/search
       │
       ▼
 RecipeController
       │
       ▼
 RecipeService
       ├──────────────────────────┐
       ▼                          ▼
QdrantRecipeRepository     RecipeRankingService
       │                          │
       ▼                          ▼
  Qdrant Cloud (gRPC)     RecipePayloadMapper
```

1. Receives a search request containing a `RecipeQuery` from the Coordinator.
2. Calls the `QdrantRecipeRepository` to perform a semantic vector search via gRPC (Qdrant handles inference directly on the server).
3. Uses the `RecipeRankingService` to rescore the results:
   - **40%**: Semantic score (from Qdrant).
   - **20%**: Title match relevance.
   - **20%**: Ingredient overlap.
   - **10%**: Cook time penalty.
   - **10%**: Main protein relevance.
4. Filters the results and returns the Top-K recipes along with nutritional information.

---

## 📂 Project Structure & File Functions

The project is strictly organized following the Layered Architecture pattern:

### 1. Root
- **`RecipeNodeApplication.java`**: The entry point for the entire Spring Boot application.

### 2. Package `config` (System Configuration)
- **`QdrantConfig.java`**: Initializes and configures the gRPC connection to Qdrant Cloud (including TLS and API Key settings).
- **`RecipeNodeProperties.java`**: A configuration class used to map properties from `application.yml` (starting with `recipe-search.*`) into a tightly validated Java record.

### 3. Package `controller` (HTTP Communication)
- **`RecipeController.java`**: Defines the REST API endpoint (`POST /recipes/search`) to receive external search requests.
- **`GlobalExceptionHandler.java`**: Centralized exception handler that intercepts errors thrown by the system and converts them into standardized JSON HTTP Responses.

### 4. Package `model` (Internal Data Structures)
- **`RecipeCandidate.java`**: Represents a raw result returned from Qdrant (before detailed mapping).
- **`RecipeDocument.java`**: Represents the complete data structure of a recipe after the payload has been decoded.

### 5. Package `repository` (Data Access)
- **`QdrantRecipeRepository.java`**: Communicates directly with the Qdrant vector database. It constructs complex filter conditions based on the `RecipeQuery`.
- **`RecipeRepositoryException.java`**: A custom error thrown when an issue occurs during communication with Qdrant.

### 6. Package `service` (Business Logic)
- **`RecipeService.java`**: The main orchestration service. It receives the query, calls the Repository to fetch raw data, and then passes it to the RankingService for scoring before returning the final output.
- **`RecipeRankingService.java`**: The heart of the search logic. It calculates multi-factor ranking scores to select the best recipes.
- **`RecipePayloadMapper.java`**: Converts the JSON payload stored in Qdrant into `RecipeDocument` and `Nutrition` objects for easy processing in Java.
- **`WarmupService.java`**: Runs automatically when the application startup completes (Using `@EventListener(ApplicationReadyEvent.class)`). It executes a dummy query to Qdrant to "warm up" the gRPC network connection, ensuring the first user requests are not delayed.

---

## 🚀 Setup Instructions

1. **System Requirements**: Java 17+ and Maven 3.8+.
2. **Environment Configuration**:
   - Copy the `.env.example` file to `.env`:
     ```bash
     cp .env.example .env
     ```
   - Fill in your Qdrant API Key in the `.env` file.
3. **Run the Application**:
   - Run directly via Maven:
     ```bash
     mvn spring-boot:run
     ```
   - By default, the application runs on **port 8081** (can be reconfigured in `application.yml`).

---

## 🛠 Usage

The primary API for this Node is `/recipes/search`. You can use cURL or Postman to test it.

### Example Request (cURL)

```bash
curl -X POST http://localhost:8081/recipes/search \
  -H "Content-Type: application/json" \
  -d '{
    "recipe_query": "delicious dinner with chicken and broccoli",
    "filters": {
      "meal_type": "main_course",
      "main_protein": "chicken",
      "max_cook_time": 45,
      "is_high_protein": true,
      "ingredients_list": ["chicken", "broccoli"]
    }
  }'
```

### Response Structure
The Node will return a list of the best recipes in JSON format:

```json
[
  {
    "item_name": "Chicken and Broccoli Stir-fry",
    "payload": "Instructions on how to quickly make chicken and broccoli stir-fry...",
    "score": 0.88,
    "ingredients": [
      { "name": "chicken breast", "quantity": 300, "unit": "g" },
      { "name": "broccoli", "quantity": 200, "unit": "g" }
    ],
    "missing_ingredients": [],
    "nutrition": {
      "calories": 350.0,
      "protein": 45.0,
      "fat": 10.0,
      "carbs": 15.0
    },
    "metadata": {
      "qdrantScore": 0.85,
      "titleRelevance": 0.90,
      "cookTime": 20,
      "matchedFilters": 3
    }
  }
]
```

### HTTP Status Codes
- **200 OK**: Successfully returns the list of results.
- **400 Bad Request**: The payload is incorrectly formatted or missing required fields.
- **500 Internal Server Error**: Internal error or connection lost with Qdrant.
