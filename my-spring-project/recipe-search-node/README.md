# Recipe Search Node

A Spring Boot microservice node for semantic recipe search using **Qdrant** vector database with server-side inference.

## Architecture

```
POST /recipes/search
       │
       ▼
RecipeSearchController
       │
       ▼
RecipeSearchService
       ├──────────────────────────┐
       ▼                          ▼
QdrantRecipeRepository     RecipeRankingService
       │                          │
       ▼                          ▼
  Qdrant Cloud (gRPC)     RecipePayloadMapper
```

**Search Pipeline:**
1. Client sends a `POST /recipes/search` with a `RecipeQuery` (text query + optional filters)
2. `QdrantRecipeRepository` performs semantic search via Qdrant's server-side inference (sentence-transformers)
3. `RecipeRankingService` applies multi-factor ranking:
   - **Qdrant semantic score** (40%) — vector similarity
   - **Title relevance** (20%) — query-to-title text matching
   - **Ingredient overlap** (20%) — query terms in ingredient names
   - **Cook time preference** (10%) — penalizes recipes exceeding max cook time
   - **Protein relevance** (10%) — matches user's desired protein type
4. Hard filters are applied for meal type, cuisine, nutrition limits, etc.
5. Top-K results are returned with metadata and missing ingredient calculations

## Prerequisites

- Java 17+
- Maven 3.8+
- A Qdrant Cloud instance with a `recipes_nutrition` collection

## Setup

1. Clone the repository
2. Copy the environment template:
   ```bash
   cp recipe-search-node/.env.example recipe-search-node/.env
   ```
3. Edit `.env` and set your `QDRANT_API_KEY`
4. Build from the project root:
   ```bash
   mvn clean install
   ```
5. Run the node:
   ```bash
   cd recipe-search-node
   mvn spring-boot:run
   ```

The server starts on **port 8081** by default.

## API

### `POST /recipes/search`

Search for recipes by natural language query with optional filters.

**Request Body:**
```json
{
  "recipe_query": "high protein low carb chicken dinner",
  "filters": {
    "meal_type": "main_course",
    "cuisine": "asian",
    "main_protein": "chicken",
    "max_cook_time": 30,
    "max_calories": 500.0,
    "min_protein": 25.0,
    "is_high_protein": true,
    "is_low_carb": true,
    "has_picture": true,
    "ingredients_list": ["chicken breast", "broccoli"],
    "ingredient_units": ["g", "g"],
    "ingredient_quantities": [200.0, 150.0]
  }
}
```

**Response:**
```json
[
  {
    "item_name": "Fast Chicken Bowl",
    "payload": "...",
    "score": 0.8542,
    "ingredients": [
      { "name": "chicken breast", "quantity": 200.0, "unit": "g" }
    ],
    "missing_ingredients": [],
    "nutrition": {
      "calories": 450.0,
      "protein": 35.0,
      "fat": 12.0,
      "carbs": 18.0
    },
    "metadata": {
      "qdrantScore": 0.82,
      "titleRelevance": 0.80,
      "cookTime": 25,
      "matchedFilters": 4
    }
  }
]
```

**Error Responses:**
| Status | Meaning |
|--------|---------|
| 400 | Invalid request body |
| 503 | Qdrant service unavailable |
| 500 | Unexpected server error |

## Configuration

Key properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | HTTP server port |
| `recipe-search.candidate-limit` | 100 | Max candidates fetched from Qdrant |
| `recipe-search.top-k` | 10 | Max results returned after ranking |
| `recipe-search.query-timeout-seconds` | 60 | gRPC query timeout |
| `recipe-search.qdrant.host` | *(env)* | Qdrant host |
| `recipe-search.qdrant.port` | 6334 | Qdrant gRPC port |
| `recipe-search.qdrant.tls` | true | Enable TLS for gRPC |
| `recipe-search.qdrant.collection` | recipes_nutrition | Qdrant collection name |
| `recipe-search.qdrant.inference-model` | sentence-transformers/all-minilm-l6-v2 | Server-side embedding model |

## Project Structure

```
recipe-search-node/
├── src/main/java/com/example/recipesearch/
│   ├── RecipeSearchNodeApplication.java   # Spring Boot entry point
│   ├── config/
│   │   ├── QdrantConfig.java              # Qdrant gRPC client bean
│   │   └── RecipeSearchProperties.java    # Type-safe config
│   ├── controller/
│   │   ├── RecipeSearchController.java    # REST API endpoint
│   │   └── GlobalExceptionHandler.java    # Error handling
│   ├── repository/
│   │   ├── QdrantRecipeRepository.java    # Qdrant query logic
│   │   ├── RecipeCandidate.java           # Raw search result
│   │   └── RecipeSearchRepositoryException.java
│   └── service/
│       ├── RecipeSearchService.java       # Search orchestration
│       ├── RecipeRankingService.java      # Multi-factor ranking
│       ├── RecipePayloadMapper.java       # Qdrant payload → domain
│       └── RecipeDocument.java            # Internal domain record
├── src/main/resources/
│   └── application.yml
├── src/test/
│   └── java/.../RecipeRankingServiceTest.java
├── .env.example
└── pom.xml
```
