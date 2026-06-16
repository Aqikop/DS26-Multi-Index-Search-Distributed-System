# ETL Pipeline Node

This folder contains the ETL (Extract, Transform, Load) components of the Distributed System. It is responsible for intercepting new recipes, parsing their ingredients, and connecting to Qdrant to calculate enriched nutritional metadata before the data is ingested into the RAG engine.

The ETL pipeline consists of two tightly coupled services:
1. **Python API** (`/etl-api`): Performs the heavy lifting (parsing logic, vector search, math).
2. **Java Spring Boot Node** (Root): Exposes the ETL service to the rest of the Java-based distributed system and orchestrates the calls.

---

## 🚀 How to Build and Run

To run the full end-to-end flow, you need to spin up all three required components in separate terminals.

### 1. Start the Python ETL API
The Python API connects to the Qdrant Cloud cluster, so it requires an API key to authenticate.

```powershell
cd etl-api
# Set your API key for the current terminal session
$env:QDRANT_API_KEY="your-qdrant-api-key-here"

# Start the FastAPI server on port 6000
uvicorn app:app --port 6000 --reload
```
*(Note: On startup, the Python API will automatically verify and create any missing Qdrant text indexes).*

### 2. Start the Java ETL Node
This is the Spring Boot microservice that acts as the bridge.

```powershell
# Open a second terminal
cd ETL-node
mvn spring-boot:run
```
*(This service runs on port `8140` by default).*

### 3. Start the Coordinator Node
The Coordinator receives user requests and triggers the ETL node.

```powershell
# Open a third terminal
cd ../coordinator
mvn spring-boot:run
```
*(This service runs on port `8080` by default).*

---

## 🧪 Testing the Integration

Once all three terminals are running without errors, you can simulate a user adding a new recipe. 

Open a **fourth** PowerShell terminal and run this single-line command:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/dishes" -Method Post -ContentType "application/json" -Body '{"name": "Spaghetti Bolognese", "ingredients": ["1 pound ground beef", "2 cups tomato sauce", "2 cloves garlic", "1 medium onion"], "cookingMethod": "Boil spaghetti until al dente. In a pan, cook ground beef with garlic and onion, then add tomato sauce. Mix with pasta."}'
```

**What to expect:**
1. You will receive a `201 Created` response.
2. The **Python API terminal** will log the processing chunks.
3. The **Coordinator terminal** will print the resulting `--- ENRICHED METADATA START ---` JSON block, fully populated with the calculated nutritional values!
