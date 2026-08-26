package dev.ncc.webhook.endpoint;

import java.time.Instant;
import java.util.UUID;

record EndpointConfigurationChanged(
    UUID endpointId, long version, boolean nameChanged, boolean urlChanged, Instant changedAt) {}
