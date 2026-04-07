package com.bol.service.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("LocalRAG API")
                .description("Knowledge-base agent: ingest PDFs into pgvector and answer questions via Ollama LLM")
                .version("1.0.0")
        )
}
