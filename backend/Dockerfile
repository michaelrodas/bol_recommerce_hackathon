# syntax=docker/dockerfile:1

# --- Build stage: compile the Kotlin/Spring Boot fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster rebuilds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build (skip tests — ArchUnit/context tests need DB + Ollama at runtime).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Run stage: slim JRE only ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
