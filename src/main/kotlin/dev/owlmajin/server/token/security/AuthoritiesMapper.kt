package dev.owlmajin.server.token.security

import org.springframework.security.oauth2.jwt.Jwt

fun interface AuthoritiesMapper {
    fun map(jwt: Jwt): Set<String>
}
