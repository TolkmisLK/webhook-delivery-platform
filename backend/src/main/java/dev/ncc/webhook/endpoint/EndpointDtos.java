package dev.ncc.webhook.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EndpointDtos {

  private EndpointDtos() {}

  public record CreateEndpointRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(max = 2048) String url,
      @NotBlank @Size(min = 16, max = 512) String secret) {}

  public record SetEndpointStatusRequest(
      @NotNull Boolean active, @NotNull @PositiveOrZero Long expectedVersion) {}

  public record RotateEndpointSecretRequest(
      @NotBlank @Size(min = 16, max = 512) String newSecret,
      @NotNull @PositiveOrZero Long expectedVersion) {}

  public record EndpointResponse(
      UUID id, String name, String url, boolean active, long version, Instant createdAt) {

    static EndpointResponse from(WebhookEndpoint endpoint) {
      return new EndpointResponse(
          endpoint.getId(),
          endpoint.getName(),
          endpoint.getUrl(),
          endpoint.isActive(),
          endpoint.getVersion(),
          endpoint.getCreatedAt());
    }
  }
}
