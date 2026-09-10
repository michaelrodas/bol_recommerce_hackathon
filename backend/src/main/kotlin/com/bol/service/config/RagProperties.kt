package com.bol.service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    @DefaultValue("5") val topK: Int,
    @DefaultValue("0.6") val similarityThreshold: Double,
    @DefaultValue("llava-phi3:3.8b") val visionModel: String
)
