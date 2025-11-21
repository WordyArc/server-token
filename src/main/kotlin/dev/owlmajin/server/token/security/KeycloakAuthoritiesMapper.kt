package dev.owlmajin.server.token.security

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class KeycloakAuthoritiesMapper : AuthoritiesMapper {
    override fun map(jwt: Jwt): Set<String> {
        val roles = mutableSetOf<String>()

        // resource_access.{client}.roles -> client_role
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access") ?: emptyMap()
        resourceAccess.forEach { (client, value) ->
            val clientMap = value as? Map<*, *> ?: return@forEach
            val clientRoles = clientMap["roles"] as? Collection<*> ?: return@forEach
            clientRoles
                .filterIsInstance<String>()
                .forEach { role ->
                    roles += "${client}_$role"
                }
        }

        // realm_access.roles
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access") ?: emptyMap()
        val realmRoles = realmAccess["roles"] as? Collection<*> ?: emptyList<Any>()
        realmRoles
            .filterIsInstance<String>()
            .forEach { roles += it }

        // groups
        val groups = jwt.getClaim<List<String>>("groups") ?: emptyList()
        roles += groups

        return roles
    }
}