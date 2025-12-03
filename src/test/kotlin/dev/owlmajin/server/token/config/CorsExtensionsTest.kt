package dev.owlmajin.server.token.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CorsExtensionsTest {

    @Test
    fun `isDisabled should return true when allowedOrigins is empty`() {
        val cors = AuthProxyProperties.Cors(
            allowedOrigins = emptyList()
        )

        assertTrue(cors.isDisabled())
    }

    @Test
    fun `isDisabled should return false when allowedOrigins is not empty`() {
        val cors = AuthProxyProperties.Cors(
            allowedOrigins = listOf("http://localhost:8080")
        )

        assertFalse(cors.isDisabled())
    }
}
