package dev.ncc.webhook.endpoint;

import java.util.UUID;

record EndpointStatusChanged(UUID endpointId, boolean active, long version) {}
