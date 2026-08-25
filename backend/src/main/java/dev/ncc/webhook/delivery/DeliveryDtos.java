package dev.ncc.webhook.delivery;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class DeliveryDtos {

  private DeliveryDtos() {}

  public record DeliveryResponse(
      UUID id,
      UUID eventId,
      String eventType,
      String endpointName,
      String endpointUrl,
      DeliveryStatus status,
      int attemptCount,
      int maxAttempts,
      Instant nextAttemptAt,
      Integer lastStatusCode,
      String lastError,
      Instant createdAt,
      Instant updatedAt) {}

  public record DeliveryStats(long total, Map<DeliveryStatus, Long> byStatus) {}
}
