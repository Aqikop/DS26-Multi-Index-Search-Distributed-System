# Coordinator Node

`coordinator` is the master orchestrator of the distributed food search system. It is built with Spring Boot and uses a custom implementation of the **Raft Consensus Algorithm** to manage the cluster state, handle leader elections, and orchestrate search requests across multiple worker nodes.

## 🏗 Architecture & Raft Consensus

The `coordinator` module acts as the entry point for all user requests and ensures high availability. It runs in a cluster where one node is the **Leader** and the others are **Followers**.

- **Leader Election**: Managed by `ConsensusService`. If a follower does not receive a heartbeat (`/ping`) from the leader within 5 seconds, it promotes itself to a **Candidate** and broadcasts a `/vote` request. The node with the majority of votes becomes the new leader.
- **Request State Machine**: The leader receives user requests and manages their lifecycle (`received` -> `decomposed` -> `recipes_found` -> `done` or `error`).
- **Worker Delegation**: The leader distributes sub-tasks (`/llm/decompose`, `/recipes/search`, `/llm/answer`) to the `llm-node` and `recipe-node` pools using `ProcessingService`.

## 📂 Key Components

- **`ClientController`**: Exposes the public REST API (`/search`, `/get`) for frontend clients. Only the Leader processes these requests.
- **`AuthController`**: Handles simple token-based authentication for the `/admin` portal (uses hardcoded config).
- **`DishController`**: Secured endpoint for adding new dishes to the Qdrant database. Routes to the ETL node.
- **`ClusterController`**: Internal endpoints (`/ping`, `/vote`, `/join`, `/copy`, etc.) used for inter-node communication and Raft consensus.
- **`ConsensusService`**: Contains the core logic for the Raft algorithm (heartbeats, elections, terms, node discovery).
- **`ProcessingService`**: The background worker thread that monitors the `requestQueue` and makes external HTTP calls to `llm-node` and `recipe-node`.
- **`RequestStorage`**: A thread-safe in-memory key-value store that holds the `UserRequest` objects and their current processing state. It also broadcasts state copies to follower nodes for fault tolerance.

## 🚀 Setup & Execution

You can run multiple instances of the coordinator to form a cluster.

1. **Run the First Node (Leader)**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --node.id=8080"
   ```

2. **Run a Second Node (Follower)**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --node.id=8081"
   ```

3. **Join the Cluster**
   Send a join request from the new node to the leader:
   ```bash
   curl -X POST "http://localhost:8080/join?id=8081"
   ```

## 🛠 Public API Usage

### 1. Initiate a Search
**Request**
```bash
curl -X POST http://localhost:8080/search \
  -H "Content-Type: application/json" \
  -d '{
    "userQuery": "I want a high protein chicken dinner under 30 minutes"
  }'
```
**Response**
Returns a tracking ID (UUID) immediately.
```text
3fa85f64-5717-4562-b3fc-2c963f66afa6
```

### 2. Poll for Results
Since processing involves multiple LLM calls and vector searches, it is asynchronous.
**Request**
```bash
curl -X GET "http://localhost:8080/get?id=3fa85f64-5717-4562-b3fc-2c963f66afa6"
```
**Response (Pending)**
```json
{
  "state": "PENDING"
}
```

**Response (Done)**
```json
{
  "state": "SUCCESS",
  "recipes": [
    {
      "item_name": "Garlic Butter Chicken",
      "score": 0.88,
      "payload": "..."
    }
  ],
  "answer": "Here are some great options for you! You can make Garlic Butter Chicken..."
}
```
