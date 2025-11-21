package dev.owlmajin.server.token.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(private val properties: AuthProxyProperties) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        properties.oidc.jwkSetUri?.let {
            return NimbusJwtDecoder.withJwkSetUri(it).build()
        }

        val issuer = requireNotNull(properties.oidc.issuerUri) {
            "Either authproxy.oidc.issuer-uri or authproxy.oidc.jwk-set-uri must be set"
        }
        return JwtDecoders.fromIssuerLocation(issuer)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowCredentials = true
            allowedOrigins = properties.cors.allowedOrigins.ifEmpty { listOf("*") }
            allowedMethods = listOf("GET", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type")
            maxAge = 3600
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http.securityMatcher("/actuator/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .build()

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        corsConfigurationSource: CorsConfigurationSource
    ): SecurityFilterChain =
        http.securityMatcher(properties.userInfoPath)
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, properties.userInfoPath).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.decoder(jwtDecoder)
                }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .logout { it.disable() }
            .build()

}
