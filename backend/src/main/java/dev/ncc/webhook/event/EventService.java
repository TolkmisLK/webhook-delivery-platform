package dev.ncc.webhook.event;

import dev.ncc.webhook.common.NotFoundException;
import dev.ncc.webhook.endpoint.WebhookEndpoint;
import dev.ncc.webhook.endpoint.WebhookEndpointRepository;
import dev.ncc.webhook.event.DeliverySubmission.AcceptedDelivery;
import dev.ncc.webhook.event.EventDtos.PublishEventRequest;
import dev.ncc.webhook.event.EventDtos.PublishEventResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class EventService {

  private final WebhookEndpointRepository endpointRepository;
  private final WebhookEventRepository eventRepository;
  private final DeliverySubmission deliverySubmission;
  private final JsonMapper objectMapper;
  private final Clock clock;

  public EventService(
      WebhookEndpointRepository endpointRepository,
      WebhookEventRepository eventRepository,
      DeliverySubmission deliverySubmission,
      JsonMapper objectMapper,
      Clock clock) {
    this.endpointRepository = endpointRepository;
    this.eventRepository = eventRepository;
    this.deliverySubmission = deliverySubmission;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public PublishEventResponse publish(PublishEventRequest request) {
    if (!request.data().isObject() && !request.data().isArray()) {
      throw new IllegalArgumentException("Event data must be a JSON object or array");
    }
    WebhookEndpoint endpoint =
        endpointRepository
            .findByIdForUpdate(request.endpointId())
            .orElseThrow(() -> new NotFoundException("Webhook endpoint was not found"));
    if (!endpoint.isActive()) {
      throw new IllegalArgumentException("Webhook endpoint is inactive");
    }

    String key = request.idempotencyKey().trim();
    var existing = eventRepository.findByEndpointIdAndIdempotencyKey(endpoint.getId(), key);
    if (existing.isPresent()) {
      AcceptedDelivery delivery =
          deliverySubmission.ensureScheduled(
              existing.get().getId(), endpoint.getId(), existing.get().getCreatedAt());
      return response(existing.get(), delivery, true);
    }

    Instant now = Instant.now(clock);
    UUID eventId = UUID.randomUUID();
    WebhookEvent event =
        new WebhookEvent(
            eventId,
            endpoint.getId(),
            request.eventType().trim(),
            renderBody(eventId, request.eventType().trim(), request.data(), now),
            key,
            now);
    eventRepository.save(event);

    AcceptedDelivery delivery =
        deliverySubmission.ensureScheduled(event.getId(), endpoint.getId(), now);
    return response(event, delivery, false);
  }

  private String renderBody(UUID id, String type, JsonNode data, Instant createdAt) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("id", id.toString());
    body.put("type", type);
    body.put("createdAt", createdAt.toString());
    body.set("data", data);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Event data could not be serialized", exception);
    }
  }

  private PublishEventResponse response(
      WebhookEvent event, AcceptedDelivery delivery, boolean duplicate) {
    return new PublishEventResponse(
        event.getId(), delivery.id(), delivery.status(), event.getCreatedAt(), duplicate);
  }
}
