package com.bol.service.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    @DefaultValue("5") val topK: Int
)
