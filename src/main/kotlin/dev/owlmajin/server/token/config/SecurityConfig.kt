package dev.owlmajin.server.token.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
// без этого приходится писать http.invoke {}, вместо http {}
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
class SecurityConfig(private val properties: AuthProxyProperties) {

    @Bean
    @Order(0)
    fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.invoke {
            securityMatcher("/actuator/**")
            authorizeHttpRequests {
                authorize(anyRequest, permitAll)
            }
            csrf { disable() }
        }

        return http.build()
    }


    @Bean
    @Order(100)
    fun apiSecurityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        corsConfigurationSource: CorsConfigurationSource
    ): SecurityFilterChain {
        http {
            securityMatcher(properties.userinfoPath)

            cors {
                if (properties.cors.isDisabled()) disable()
                else configurationSource = corsConfigurationSource
            }

            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, properties.userinfoPath, permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { this.jwtDecoder = jwtDecoder }
            }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            csrf { disable() }
            logout { disable() }
        }

        return http.build()
    }

}
