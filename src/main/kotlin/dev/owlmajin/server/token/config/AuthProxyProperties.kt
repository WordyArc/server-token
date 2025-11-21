package dev.owlmajin.server.token.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("authproxy")
class AuthProxyProperties(
    val userInfoPath: String = "/userinfo",
    val cors: Cors = Cors(),
    val oidc: Oidc = Oidc(),
) {
    data class Cors(
        val allowedOrigins: List<String> = emptyList(),
    )

    data class Oidc(
        val issuerUri: String? = null,
        val jwkSetUri: String? = null,
    )
}
