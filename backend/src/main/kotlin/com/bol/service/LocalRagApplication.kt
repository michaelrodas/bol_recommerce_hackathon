package com.bol.service

import com.bol.service.config.RagProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(RagProperties::class)
class LocalRagApplication

fun main(args: Array<String>) {
    runApplication<LocalRagApplication>(*args)
}
