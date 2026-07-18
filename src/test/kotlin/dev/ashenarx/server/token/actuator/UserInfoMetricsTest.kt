package dev.ashenarx.server.token.actuator

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserInfoMetricsTest {

    @Test
    fun `onSuccess should increment success counter and set status to 1`() {
        // given
        val registry = SimpleMeterRegistry()
        val metrics = UserInfoMetrics(registry)

        // when
        metrics.onSuccess()

        // then
        val successCounter = registry.get("userinfo_request")
            .tag("success", "true")
            .counter()
        val failureCounter = registry.get("userinfo_request")
            .tag("success", "false")
            .counter()
        val statusGauge = registry.get("userinfo_status")
            .gauge()

        assertEquals(1.0, successCounter.count())
        assertEquals(0.0, failureCounter.count())
        assertEquals(1.0, statusGauge.value())
    }

    @Test
    fun `onFailure should increment failure counter and set status to 0`() {
        // given
        val registry = SimpleMeterRegistry()
        val metrics = UserInfoMetrics(registry)

        // when
        metrics.onFailure()

        // then
        val successCounter = registry.get("userinfo_request")
            .tag("success", "true")
            .counter()
        val failureCounter = registry.get("userinfo_request")
            .tag("success", "false")
            .counter()
        val statusGauge = registry.get("userinfo_status")
            .gauge()

        assertEquals(0.0, successCounter.count())
        assertEquals(1.0, failureCounter.count())
        assertEquals(0.0, statusGauge.value())
    }

}