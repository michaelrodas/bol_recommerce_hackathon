package com.bol.service.service

import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.reader.pdf.PagePdfDocumentReader
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig
import org.springframework.ai.transformer.splitter.TokenTextSplitter
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.core.io.ByteArrayResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DocumentIngestionService(
    private val vectorStore: VectorStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Reads a PDF file, splits it into chunks, and stores embeddings in pgvector.
     * Returns the number of chunks stored.
     */
    fun ingest(file: MultipartFile): Int {
        log.info("Ingesting PDF: ${file.originalFilename} (${file.size} bytes)")

        val resource = NamedByteArrayResource(file.bytes, file.originalFilename ?: "document.pdf")

        val config = PdfDocumentReaderConfig.builder()
            .withPagesPerDocument(1)
            .build()

        val reader = PagePdfDocumentReader(resource, config)
        val pages: List<Document> = reader.get()

        // 256 tokens per chunk — stays well within nomic-embed-text's 2048-token context window
        val splitter = TokenTextSplitter(
            /* defaultChunkSize */ 256,
            /* minChunkSizeChars */ 50,
            /* minChunkLengthToEmbed */ 5,
            /* maxNumChunks */ 10000,
            /* keepSeparator */ true
        )
        val chunks: List<Document> = splitter.apply(pages)

        // Tag each chunk with the source filename for traceability
        val tagged = chunks.map { doc ->
            Document(
                doc.text!!,
                doc.metadata + mapOf("source" to (file.originalFilename ?: "unknown"))
            )
        }

        vectorStore.add(tagged)

        log.info("Stored ${tagged.size} chunks from ${file.originalFilename}")
        return tagged.size
    }

    /** ByteArrayResource that carries a filename (required by the PDF reader). */
    private class NamedByteArrayResource(bytes: ByteArray, private val filename: String) :
        ByteArrayResource(bytes) {
        override fun getFilename() = filename
    }
}
