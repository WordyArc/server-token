package dev.owlmajin.server.token.actuator

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class UserInfoMetrics(meterRegistry: MeterRegistry) {
    private val userinfoSuccess = meterRegistry.counter("userinfo_request", "success", "true")
    private val userinfoFailure = meterRegistry.counter("userinfo_request", "success", "false")
    private val userinfoStatus = meterRegistry.gauge("userinfo_status", AtomicInteger(0))

    fun onSuccess() {
        userinfoSuccess.increment()
        userinfoStatus.set(1)
    }

    fun onFailure() {
        userinfoFailure.increment()
        userinfoStatus.set(0)
    }
}
