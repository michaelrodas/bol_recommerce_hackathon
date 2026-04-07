package com.bol.service.controller

import com.bol.service.service.DocumentIngestionService
import com.bol.service.service.RagResponse
import com.bol.service.service.RagService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
@Tag(name = "LocalRAG", description = "Ingest PDF documents and query the knowledge base")
class RagController(
    private val ingestionService: DocumentIngestionService,
    private val ragService: RagService
) {

    @Operation(
        summary = "Upload PDF documents",
        description = "Parses each PDF into chunks, embeds them, and stores the vectors in pgvector.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Documents ingested successfully",
                content = [Content(schema = Schema(implementation = UploadResponse::class))]
            )
        ]
    )
    @PostMapping("/documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDocuments(
        @RequestParam("files") files: List<MultipartFile>
    ): ResponseEntity<UploadResponse> {
        val results = files.map { file ->
            val chunks = ingestionService.ingest(file)
            UploadResult(filename = file.originalFilename ?: "unknown", chunks = chunks)
        }
        return ResponseEntity.ok(UploadResponse(results))
    }

    @Operation(
        summary = "Ask a question",
        description = "Retrieves the most relevant document chunks from pgvector and uses the Ollama LLM to generate a grounded answer.",
        requestBody = SwaggerRequestBody(
            required = true,
            content = [Content(schema = Schema(implementation = ChatRequest::class))]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Answer generated",
                content = [Content(schema = Schema(implementation = RagResponse::class))]
            )
        ]
    )
    @PostMapping("/chat")
    fun chat(@RequestBody request: ChatRequest): ResponseEntity<RagResponse> {
        val response = ragService.answer(request.question)
        return ResponseEntity.ok(response)
    }
}

@Schema(description = "Chat request payload")
data class ChatRequest(
    @field:Schema(description = "The question to ask the knowledge base", example = "What is the main topic of the document?")
    val question: String
)

@Schema(description = "Result of a document ingestion request")
data class UploadResponse(val results: List<UploadResult>)

@Schema(description = "Per-file ingestion result")
data class UploadResult(
    @field:Schema(description = "Original filename", example = "report.pdf")
    val filename: String,
    @field:Schema(description = "Number of chunks stored in pgvector", example = "42")
    val chunks: Int
)
