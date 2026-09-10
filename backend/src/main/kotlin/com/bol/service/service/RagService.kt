package com.bol.service.service

import com.bol.service.config.RagProperties
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.util.MimeType
import org.springframework.web.multipart.MultipartFile
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Service
class RagService(
    chatClientBuilder: ChatClient.Builder,
    private val vectorStore: VectorStore,
    private val ragProperties: RagProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatClient: ChatClient = chatClientBuilder.build()

    fun answer(question: String, image: MultipartFile? = null): RagResponse {
        log.info("RAG query: \"$question\" (image attached: ${image != null})")

        // Prepare image bytes once so we don't re-read the stream later
        val mimeType = image?.let { MimeType.valueOf(it.contentType ?: "image/jpeg") }
        val imageBytes = image?.let { resizeImage(it.bytes, mimeType!!) }

        // Step 1 — when a photo is provided, ask the model what it sees.
        // The resulting description is merged into the vector search query so that
        // pgvector retrieves SOP chunks relevant to BOTH the question and the
        // actual physical state of the item, not just the text question alone.
        val visualObservation = if (imageBytes != null && mimeType != null) {
            describeImage(imageBytes, mimeType, image?.originalFilename, question)
                .also { obs -> if (obs != null) log.info("Visual observation: $obs") }
        } else null

        // Step 2 — similarity search with the enriched query
        val searchQuery = if (visualObservation != null) "$question\n$visualObservation" else question
        val hits = retrieve(searchQuery)

        if (hits.isEmpty()) {
            log.warn("No relevant SOP chunks found for: $question")
            return RagResponse(
                answer = "I could not find any relevant information in the knowledge base to answer your question.",
                sources = emptyList(),
                responseTimeMs = 0
            )
        }

        // Step 3 — format retrieved SOP chunks with source attribution per chunk
        log.debug("pgvector returned ${hits.size} chunks:")
        hits.forEachIndexed { i, doc ->
            val source = doc.metadata["source"] as? String ?: "unknown"
            log.debug("  [${i + 1}] source=$source | score=${doc.score} | text=${doc.text?.take(240)}...")
        }

        val context = hits.mapIndexed { i, doc ->
            val source = doc.metadata["source"] as? String ?: "SOP"
            "[${i + 1}] ($source)\n${doc.text}"
        }.joinToString("\n\n")

        // Step 4 — final LLM call (always text-only: visual context already captured in system prompt)
        val start = System.currentTimeMillis()
        val answer = chatClient.prompt()
            .system(buildSystemPrompt(context, visualObservation))
            .user(question)
            .call()
            .content() ?: "No response generated."
        val responseTimeMs = System.currentTimeMillis() - start

        val sources = hits.mapNotNull { it.metadata["source"] as? String }.distinct()
        log.info("Answer generated from ${hits.size} SOP chunks in ${responseTimeMs}ms (sources: $sources)")
        return RagResponse(answer = answer, sources = sources, responseTimeMs = responseTimeMs)
    }

    /**
     * Pure retrieval step — embeds [query] and returns the top matching chunks from
     * pgvector. Shared by [answer] and the /api/retrieve eval endpoint so both exercise
     * the exact same retrieval path (no drift between prod and eval).
     *
     * @param topK number of chunks to return; defaults to the configured value.
     * @param threshold optional minimum similarity; when null, all topK are returned.
     */
    fun retrieve(query: String, topK: Int? = null, threshold: Double? = null): List<Document> {
        val builder = SearchRequest.builder()
            .query(query)
            .topK(topK ?: ragProperties.topK)
        builder.similarityThreshold(threshold ?: ragProperties.similarityThreshold)
        return vectorStore.similaritySearch(builder.build()) ?: emptyList()
    }

    // Asks the model to describe what is visible in the photo, focused on the
    // operator's question. The output feeds the RAG query in Step 2 and is also
    // surfaced to the model again in the final prompt as a named section.
    private fun describeImage(
        imageBytes: ByteArray,
        mimeType: MimeType,
        filename: String?,
        question: String
    ): String? = try {
        val resource = object : ByteArrayResource(imageBytes) {
            override fun getFilename() = filename
        }
        log.info("Describing image with vision model: ${ragProperties.visionModel}")
        chatClient.prompt()
            .options(OllamaOptions.builder().model(ragProperties.visionModel).build())
            .user { spec ->
                spec.text(
                    "You are inspecting a returned item in a warehouse. " +
                    "The operator is asking: \"$question\"\n" +
                    "Describe only what you can observe in the image that is relevant to answering this question. " +
                    "Focus on: packaging condition, seal integrity, visible damage, signs of use, completeness. " +
                    "Be concise and factual — 2 to 4 sentences. Do not speculate about what you cannot see."
                )
                spec.media(mimeType, resource)
            }
            .call()
            .content()
    } catch (e: Exception) {
        log.warn("Image description step failed, proceeding text-only: ${e.message}")
        null
    }

    private fun buildSystemPrompt(context: String, visualObservation: String?): String {
        val visualSection = if (visualObservation != null) """

            --- VISUAL OBSERVATION (photo of the returned item) ---
            $visualObservation
            --- END VISUAL OBSERVATION ---
            """ else ""

        val withImageRule = if (visualObservation != null)
            "Cross-reference the VISUAL OBSERVATION with the SOP CONTEXT to reach a concrete decision about this specific item."
        else
            "Answer based solely on the SOP CONTEXT."

        return """
            You are a Return Assessment Assistant for warehouse operators.
            Your job is to translate Standard Operating Procedures (SOPs) into clear, step-by-step actions that an operator can immediately follow on the floor.

            You receive:
            - SOP CONTEXT: Relevant excerpts from the official returns manual.
            ${if (visualObservation != null) "- VISUAL OBSERVATION: What was observed in the attached photo of the returned item." else ""}
            - The operator's question (in the user message).

            Rules you must follow:
            1. Base ALL decisions strictly on the SOP CONTEXT. Never use outside knowledge or invent policies.
            2. $withImageRule
            3. Write in the imperative mood. Be direct and actionable ("Inspect the seal", "Apply a red sticker").
            4. Use numbered steps or bullet points. Bold critical conditions (**IF the seal is broken**).
            5. Start immediately with the first action. No preamble, no filler phrases.
            6. If the SOP CONTEXT does not cover the situation, output exactly: "No relevant instructions found in the SOP. Please escalate to your supervisor."
            $visualSection
            --- SOP CONTEXT ---
            $context
            --- END SOP CONTEXT ---
        """.trimIndent()
    }

    // Downscales the image so its longest side is at most maxDimension pixels.
    // Vision models tokenise images into patches; large images exhaust memory before inference starts.
    private fun resizeImage(bytes: ByteArray, mimeType: MimeType, maxDimension: Int = 336): ByteArray {
        val original: BufferedImage = ImageIO.read(bytes.inputStream()) ?: return bytes
        val w = original.width
        val h = original.height
        if (w <= maxDimension && h <= maxDimension) return bytes

        val scale = maxDimension.toDouble() / maxOf(w, h)
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()

        val resized = BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB)
        val g = resized.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(original, 0, 0, nw, nh, null)
        g.dispose()

        val format = if (mimeType.subtype.contains("png")) "PNG" else "JPEG"
        val out = ByteArrayOutputStream()
        ImageIO.write(resized, format, out)
        log.info("Image resized from ${w}x${h} to ${nw}x${nh} (${bytes.size / 1024}KB → ${out.size() / 1024}KB)")
        return out.toByteArray()
    }
}

@Schema(description = "RAG answer with source attribution")
data class RagResponse(
    @field:Schema(description = "LLM-generated answer grounded in the knowledge base")
    val answer: String,
    @field:Schema(description = "Filenames of the documents that contributed to the answer")
    val sources: List<String>,
    @field:Schema(description = "LLM response time in milliseconds")
    val responseTimeMs: Long
)
