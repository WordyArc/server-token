package dev.owlmajin.server.token

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ServerTokenApplication

fun main(args: Array<String>) {
	runApplication<ServerTokenApplication>(*args)
}
