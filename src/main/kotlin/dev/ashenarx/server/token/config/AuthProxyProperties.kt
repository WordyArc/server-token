package dev.ashenarx.server.token.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("authproxy")
class AuthProxyProperties(
    val userinfoPath: String = "/userinfo",
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
