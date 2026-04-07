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

//        print(context)
        val systemPrompt = """
            You are an expert Return Assessment Assistant for warehouse operators. Your job is to read convoluted Standard Operating Procedures (SOPs) and translate them into simple, direct, step-by-step instructions for the operator on the floor.

            You will receive "CONTEXT" from the official return manual and a "User Question" from the operator.
            
            Follow these absolute rules:
            1. NO OUTSIDE KNOWLEDGE: Base your instructions strictly on the CONTEXT. Do not invent policies or use outside knowledge.
            2. BE DIRECT AND ACTIONABLE: Write in the imperative mood (e.g., "Inspect the screen", "Apply a red sticker").
            3. NO FLUFF: Do not include conversational filler like "Based on the text..." or "Here are your instructions:". Start immediately with the first step.
            4. FORMAT: Use short, numbered lists or bullet points for readability. Bold critical conditions (e.g., "IF the seal is broken").
            5. MISSING INFO: If the CONTEXT does not contain the answer, do not guess. Output exactly: "No relevant instructions found in the SOP. Please escalate to your supervisor."
            
            ---
            EXAMPLES:
            
            CONTEXT: "The packaging is opened if:
            The original seal has been broken. The shrink wrap is partially broken and the item can be taken out or fall out. (Poly)bag is torn / opened. The seal is not original. There are bubbles or dirt underneath the seal or it is placed in a different position.
            Note: Check for fraud if package opened: Guidelines FIC
            
            The packaging is not opened if: The original seal is still completely intact. The shrink wrap is partially broken, but the product cannot fall out or be taken out."
            User Question: “There are bubbles under the seal of the package. Is it opened?”
            Your Response:
            Bubbles or dirt underneath the seal means that the packaging is opened.
            
            CONTEXT: "Articles cannot go to RGR if: Packaging is not original or damaged. Product is incomplete or damaged. Product has signs of usage that we can't remove: Food, oil, water or glue residues. Dirt in unreachable areas and/or other stains that you can't wipe away. Smell."
            User Question: "Customer returned a blender and it smells like smoke. Is it suitable for RGR?"
            Your Response:
            No. Product has signs of usage that we can't remove.
            ---

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
