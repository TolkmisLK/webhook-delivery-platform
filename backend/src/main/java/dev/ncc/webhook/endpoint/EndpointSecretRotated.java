package dev.ncc.webhook.endpoint;

import java.time.Instant;
import java.util.UUID;

record EndpointSecretRotated(UUID endpointId, long version, Instant rotatedAt) {}
