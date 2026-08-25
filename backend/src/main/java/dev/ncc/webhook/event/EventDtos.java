package dev.ncc.webhook.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class EventDtos {

  private EventDtos() {}

  public record PublishEventRequest(
      @NotNull UUID endpointId,
      @NotBlank @Size(max = 160) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String eventType,
      @NotNull JsonNode data,
      @NotBlank @Size(max = 200) String idempotencyKey) {}

  public record PublishEventResponse(
      UUID eventId, UUID deliveryId, String status, Instant acceptedAt, boolean duplicate) {}
}
