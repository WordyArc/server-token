package dev.owlmajin.server.token.web

import dev.owlmajin.server.token.actuator.UserInfoMetrics
import dev.owlmajin.server.token.security.AuthoritiesMapper
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class UserInfoController(
    private val authoritiesMapper: AuthoritiesMapper,
    private val metrics: UserInfoMetrics,
) {
    /**
     * Контракт:
     * - Берём все claims из JWT;
     * - Добавляем поле "roles" - нормализованные роли;
     * - Отдаём как JSON.
     *
     * Spring замапит JwtAuthenticationToken из текущей аутентификации
     * (BearerTokenAuthenticationFilter уже отработал).
     */
    @GetMapping("\${authproxy.userinfo-path:/userinfo}")
    fun userInfo(auth: JwtAuthenticationToken): Map<String, Any> {
        try {
            val jwt: Jwt = auth.token
            val claims = jwt.claims.toMutableMap()

            val roles = authoritiesMapper.map(jwt)
            claims["roles"] = roles

            metrics.onSuccess()
            return claims
        } catch (ex: Exception) {
            metrics.onFailure()
            throw AccessDeniedException("Access denied", ex)
        }
    }
}
