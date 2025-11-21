package dev.owlmajin.server.token.config

internal fun AuthProxyProperties.Cors.isDisabled() = allowedOrigins.isEmpty()