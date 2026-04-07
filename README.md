# Bol Recommerce Hackathon

This repository contains the code for the Bol Recommerce Hackathon project. It is split into two main directories: a `frontend` and a `backend`, which work together to form a full-stack application leveraging local Large Language Models (LLMs) and Retrieval-Augmented Generation (RAG).

![Visualization of the codebase](./images/diagram.svg)

## Architecture Overview

```mermaid
graph TD
    %% Define Styles
    classDef frontend fill:#61DAFB,stroke:#333,stroke-width:2px,color:#000
    classDef backend fill:#6DB33F,stroke:#333,stroke-width:2px,color:#fff
    classDef ai fill:#FF9900,stroke:#333,stroke-width:2px,color:#000
    classDef db fill:#336791,stroke:#333,stroke-width:2px,color:#fff
    classDef client fill:#f9f9f9,stroke:#333,stroke-width:2px

    %% Components
    Client((User Browser)):::client
    
    subgraph Frontend [React Frontend / Vite]
        UI["Chat Interface\n(React 19, TypeScript)"]:::frontend
    end
    
    subgraph Backend [Spring Boot Backend]
        API["REST Controllers\n(Kotlin, Spring Web)"]:::backend
        RAG["RAG Service\n(Spring AI)"]:::backend
        Ingest["Document Ingestion\n(PDF Reader)"]:::backend
    end
    
    subgraph Models [Ollama Local LLMs]
        LLM["Chat Model\n(e.g., qwen2.5)"]:::ai
        Embed["Embedding Model\n(e.g., nomic-embed-text)"]:::ai
    end
    
    subgraph Database [PostgreSQL]
        PGV[(pgvector Database\nKnowledge Base)]:::db
    end

    %% Interactions
    Client <-->|REST / JSON| UI
    UI <-->|POST /api/chat| API
    UI -->|POST /api/documents| API
    
    API <--> RAG
    API --> Ingest
    
    %% RAG Flow
    RAG <-->|Embed Query| Embed
    RAG <-->|Similarity Search| PGV
    RAG <-->|Prompt + Context| LLM
    
    %% Ingestion Flow
    Ingest -->|PDF Chunks| Embed
    Embed -->|Vectors| PGV

```

## Tech Stack

### Frontend (`hackathon_frontend/`)
The frontend is a modern, lightweight web application built to interact with the backend AI services.
- **Framework:** React 19
- **Language:** TypeScript
- **Build Tool:** Vite
- **Styling:** CSS
- **Linting:** ESLint

### Backend (`backend/`)
The backend is a robust REST API that handles the business logic, document parsing, and AI integrations using the Spring ecosystem.
- **Framework:** Spring Boot 3.4
- **Language:** Kotlin 2.1
- **AI Integration:** Spring AI 1.0.0 (Ollama for local chat and embeddings)
- **Vector Database:** pgvector (PostgreSQL)
- **Document Processing:** Spring AI PDF Document Reader
- **API Documentation:** Swagger / OpenAPI (Springdoc)
- **Build Tool:** Maven

## Getting Started
Please refer to the respective directories for specific setup and execution instructions.