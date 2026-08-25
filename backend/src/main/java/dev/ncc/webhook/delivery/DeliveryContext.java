package dev.ncc.webhook.delivery;

import java.time.Instant;
import java.util.UUID;

record DeliveryContext(
    UUID jobId,
    UUID eventId,
    String eventType,
    String endpointUrl,
    String secret,
    String body,
    Instant eventCreatedAt,
    int completedAttempts,
    int maxAttempts) {}
