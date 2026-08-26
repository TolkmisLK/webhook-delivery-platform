package dev.ncc.webhook.delivery;

import java.util.UUID;

record DeliveryStateChanged(
    UUID jobId,
    DeliveryStatus previousStatus,
    DeliveryStatus status,
    DeliveryStateChangeSource source) {}
