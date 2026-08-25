package dev.ncc.webhook.delivery;

import java.time.Instant;
import java.util.List;
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

  public record DeliveryAttemptResponse(
      int attemptNumber,
      DeliveryStatus outcome,
      Integer statusCode,
      String errorMessage,
      String responseExcerpt,
      long durationMs,
      Instant startedAt,
      Instant finishedAt) {}

  public record DeliveryDetailResponse(
      DeliveryResponse delivery, List<DeliveryAttemptResponse> attempts) {}

  public record DeliveryStats(long total, Map<DeliveryStatus, Long> byStatus) {}
}
