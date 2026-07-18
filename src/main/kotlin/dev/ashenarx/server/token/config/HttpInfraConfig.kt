package dev.ashenarx.server.token.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class HttpInfraConfig(private val properties: AuthProxyProperties) {

    @Bean
    fun jwtDecoder(): JwtDecoder =
        properties.oidc.jwkSetUri
            ?.let { NimbusJwtDecoder.withJwkSetUri(it).build() }
            ?: JwtDecoders.fromIssuerLocation(
                requireNotNull(properties.oidc.issuerUri) { "Either authproxy.oidc.issuer-uri or authproxy.oidc.jwk-set-uri must be set" }
            )


    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowCredentials = true
            allowedOrigins = properties.cors.allowedOrigins
            allowedMethods = listOf("GET", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type")
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}