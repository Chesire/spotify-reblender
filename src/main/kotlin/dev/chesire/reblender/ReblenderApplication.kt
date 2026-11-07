package dev.chesire.reblender

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ReblenderApplication

fun main(args: Array<String>) {
    runApplication<ReblenderApplication>(*args)
}
