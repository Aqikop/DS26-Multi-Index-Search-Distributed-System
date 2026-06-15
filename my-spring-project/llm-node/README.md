# LLM Node

`llm-node` is a hybrid microservice (Spring Boot + Python/FastAPI) that acts as the intelligence layer of the distributed food search system. It utilizes LangChain and Google's Gemini 2.5 Flash Lite to parse natural language user queries and generate friendly food recommendations.

## 🏗 Data Flow Architecture

This module is split into two interoperable layers:
1. **Java Proxy (`llm-node`)**: An API Gateway that receives requests from the Coordinator and proxies them to the Python backend without the overhead of JSON serialization.
2. **Python Backend (`python-llm-api`)**: A FastAPI service that manages the LangChain pipelines, prompts, and interacts with the Gemini API.

```text
POST /llm/decompose
POST /llm/answer
         │
         ▼
    LLMController (Java Proxy)
         │
         ▼
     LLMService (Java)
         │ (HTTP Pass-through)
         ▼
  app.py (FastAPI Python)
         │
         ▼
   llm.py (LangChain & Gemini)
```

---

## 📂 Project Structure & File Functions

### 1. Java Layer (`/src/main/java/com/example/llmnode`)
- **`LLMApplication.java`**: The Spring Boot entry point.
- **`config/RestTemplateConfig.java`**: Configures the HTTP client used to forward requests to the Python layer.
- **`controller/LLMController.java`**: Exposes the REST API endpoints (`/llm/decompose` and `/llm/answer`). Takes in flexible JSON formats.
- **`service/LLMService.java`**: Forwards requests as raw `JsonNode` (or strings) to the Python API, avoiding expensive serialization loops.

### 2. Python Layer (`/python-llm-api`)
- **`app.py`**: The FastAPI application. Uses Pydantic `Field(alias="...")` to perfectly map Java's `camelCase` to Python's `snake_case`. Also handles the LLM warmup process upon startup.
- **`llm.py`**: The core AI logic. Uses `ChatGoogleGenerativeAI` to power two pipelines:
  - **Decompose & Route**: Parses messy user queries into a strict JSON payload matching `RecipeQuery.java`.
  - **Answer Generation**: Formats retrieved Qdrant items (or `RecipeQueryResult` objects) into natural, conversational responses.

---

## 🚀 Setup Instructions

1. **System Requirements**: 
   - Java 17+ and Maven 3.8+
   - Python 3.10+
2. **Environment Configuration**:
   - You need a `.env` file at the root of the project with your API keys:
     ```env
     GOOGLE_API_KEY=your_gemini_key_here
     ```
3. **Run the Python Backend**:
   - Navigate to the python directory:
     ```bash
     cd python-llm-api
     ```
   - Install dependencies:
     ```bash
     pip install -r requirements.txt
     ```
   - Run FastAPI:
     ```bash
     uvicorn app:app --host 127.0.0.1 --port 5000
     ```
4. **Run the Java Proxy**:
   - In the `llm-node` root directory, run:
     ```bash
     mvn spring-boot:run
     ```
   - By default, the proxy runs on **port 8120**.

---

## 🛠 Usage

The Coordinator calls this node during two stages: **Decompose** (to parse the initial intent) and **Answer** (to format the final result).

### 1. Decompose Query

Extracts search filters from a natural language string.

**Request (cURL)**
```bash
curl -X POST http://localhost:8120/llm/decompose \
  -H "Content-Type: application/json" \
  -d '{
    "userQuery": "I want a high protein chicken dinner under 30 minutes"
  }'
```

**Response (JSON)**
```json
{
  "recipe_query": "high protein chicken dinner",
  "filters": {
    "meal_type": "main_course",
    "cuisine": null,
    "main_protein": "chicken",
    "max_cook_time": 30,
    "is_high_protein": true,
    "max_sodium": null
  },
  "state": null
}
```

### 2. Generate Final Answer

Given a user's question and a set of retrieved recipes, the LLM will generate a helpful textual response.

**Request (cURL)**
```bash
curl -X POST http://localhost:8120/llm/answer \
  -H "Content-Type: application/json" \
  -d '{
    "userQuery": "What can I cook with these?",
    "recipeQueryResults": [
      {
        "item_name": "Garlic Butter Chicken",
        "score": 0.88,
        "payload": "Detailed instructions here..."
      }
    ]
  }'
```

**Response (JSON)**
```json
{
  "answer": "Here are some great options for you! You can make Garlic Butter Chicken, which perfectly matches your query..."
}
```
