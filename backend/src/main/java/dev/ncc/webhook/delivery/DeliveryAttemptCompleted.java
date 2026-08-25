package dev.ncc.webhook.delivery;

import java.util.UUID;

record DeliveryAttemptCompleted(
    UUID jobId, int attemptNumber, DeliveryStatus outcome, Integer statusCode, long durationMs) {}
