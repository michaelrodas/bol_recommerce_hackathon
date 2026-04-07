package com.bol.service.service

import com.bol.service.config.RagProperties
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class RagService(
    chatClientBuilder: ChatClient.Builder,
    private val vectorStore: VectorStore,
    private val ragProperties: RagProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val chatClient: ChatClient = chatClientBuilder.build()

    /**
     * Answers a question using Retrieval-Augmented Generation:
     * 1. Embed the question and retrieve the top-K most similar document chunks
     * 2. Inject those chunks as context into a system prompt
     * 3. Let the LLM generate a grounded answer
     */
    fun answer(question: String): RagResponse {
        log.info("RAG query: \"$question\"")

        val hits = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(ragProperties.topK)
                .build()
        ) ?: emptyList()

        if (hits.isEmpty()) {
            log.warn("No relevant documents found for query: $question")
            return RagResponse(
                answer = "I could not find any relevant information in the knowledge base to answer your question.",
                sources = emptyList()
            )
        }

        val context = hits.mapIndexed { i, doc ->
            "[${i + 1}] ${doc.text}"
        }.joinToString("\n\n")

        val systemPrompt = """
            You are a helpful assistant with access to a knowledge base.
            Answer the user's question strictly based on the context below.
            If the answer is not present in the context, say "I don't have enough information in the knowledge base to answer that."
            Do not make up information.

            --- CONTEXT ---
            $context
            --- END CONTEXT ---
        """.trimIndent()

        val answer = chatClient.prompt()
            .system(systemPrompt)
            .user(question)
            .call()
            .content() ?: "No response generated."

        val sources = hits.mapNotNull { it.metadata["source"] as? String }.distinct()

        log.info("Answer generated from ${hits.size} chunks (sources: $sources)")
        return RagResponse(answer = answer, sources = sources)
    }
}

@Schema(description = "RAG answer with source attribution")
data class RagResponse(
    @field:Schema(description = "LLM-generated answer grounded in the knowledge base")
    val answer: String,
    @field:Schema(description = "Filenames of the documents that contributed to the answer")
    val sources: List<String>
)
