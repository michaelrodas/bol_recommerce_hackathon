# Backend - Bol Recommerce Hackathon

This backend is built using Spring Boot 3.4 and Kotlin. It provides a Retrieval-Augmented Generation (RAG) REST API using local LLMs (via Ollama) and a PostgreSQL pgvector database for similarity search.

## Prerequisites
Ensure you have the following installed on your machine:
- **Java 21**
- **Docker** and **Docker Compose**
- **Maven** (Optional, you can use the included `mvnw` wrapper)

## Getting Started

### 1. Start External Dependencies (Database and Ollama)
Before running the Spring Boot application, you need to spin up the local PostgreSQL (pgvector) database and the Ollama LLM container.

Navigate to this `backend` directory and run:
```bash
docker-compose up -d
```

> **Note:** The first time you run this, it will download the Ollama container and automatically pull the required AI models (`qwen2.5:3b-instruct`, `llama3.2`, and `nomic-embed-text`). This might take a few minutes depending on your internet connection.

### 2. Run the Spring Boot Application
Once the database and Ollama are healthy and running, you can start the application:

```bash
# Using the Maven Wrapper (macOS/Linux)
./mvnw spring-boot:run

# Using the Maven Wrapper (Windows)
mvnw.cmd spring-boot:run
```

Alternatively, you can run the `LocalRagApplication.kt` class directly from your IDE.

### 3. Access Swagger UI
When the application is running, you can explore and test the endpoints via the generated Swagger interface at:
- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Useful Endpoints
- `POST /api/documents` - Upload PDFs to parse and embed them into the vector database.
- `POST /api/chat` - Query the knowledge base.
