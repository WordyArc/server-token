package dev.ashenarx.server.token.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class KeycloakAuthoritiesMapperTest {

    private val mapper = KeycloakAuthoritiesMapper()

    @Test
    fun `map should extract roles from resource_access and realm_access and groups`() {
        // given
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claims {
                it["realm_access"] = mapOf("roles" to listOf("admin", "offline_access", 123))
                it["resource_access"] = mapOf(
                    "account" to mapOf("roles" to listOf("view", "manage")),
                    "client-app" to mapOf("roles" to listOf("user"))
                )
                it["groups"] = listOf("/dev", "/ops")
            }.build()

        // when
        val roles = mapper.map(jwt)

        // then
        assertEquals(
            setOf(
                "account_view", "account_manage", "client-app_user",
                "/dev", "/ops",
                "admin", "offline_access"
            ),
            roles
        ) { "Incorrect roles" }
    }

    @Test
    fun `map should ignore non-map entries in resource_access`() {
        // given
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claims {
                it["resource_access"] = mapOf(
                    "account" to mapOf("roles" to listOf("view", "manage")),
                    "client-app" to listOf("user"),
                    "frontend-app" to "admin"
                )
            }.build()

        // when
        val roles = mapper.map(jwt)

        // then
        assertEquals(
            setOf("account_view", "account_manage"),
            roles
        )
    }

    @Test
    fun `map should ignore non-collection roles in resource_access`() {
        // given
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claims {
                it["resource_access"] = mapOf(
                    "client1" to mapOf("roles" to "string-non-collection"),
                    "client2" to mapOf("roles" to listOf("role2"))
                )
            }.build()

        // when
        val roles = mapper.map(jwt)

        // then
        assertEquals(setOf("client2_role2"), roles)
    }

//    @Test
//    fun `map should handle missing claims`() {
//        // given
//        val jwt = Jwt.withTokenValue("test-token")
//            .header("alg", "none")
//            .build()
//
//        // when
//        val roles = mapper.map(jwt)
//
//        // then
//        assertTrue(roles.isEmpty())
//    }

    @Test
    fun `map should deduplicate roles across claims`() {
        // given
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claims {
                it["resource_access"] = mapOf("account" to mapOf("roles" to listOf("view")))
                it["realm_access"] = mapOf("roles" to listOf("account_view"))
                it["groups"] = listOf("account_view")
            }.build()

        // when
        val roles = mapper.map(jwt)

        // then
        assertEquals(setOf("account_view"), roles)
    }

    @Test
    fun `map should deduplicate roles within claim`() {
        // given
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claims {
                it["resource_access"] = mapOf("client1" to mapOf("roles" to listOf("admin", "admin")))
            }.build()

        // when
        val roles = mapper.map(jwt)

        // then
        assertEquals(setOf("client1_admin"), roles)
    }

}