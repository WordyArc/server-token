package dev.ashenarx.server.token.config

internal fun AuthProxyProperties.Cors.isDisabled() = allowedOrigins.isEmpty()