package dev.ncc.webhook.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

  @Id private UUID id;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Column(name = "event_type", nullable = false, length = 160)
  private String eventType;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected WebhookEvent() {}

  WebhookEvent(
      UUID id,
      UUID endpointId,
      String eventType,
      String body,
      String idempotencyKey,
      Instant createdAt) {
    this.id = id;
    this.endpointId = endpointId;
    this.eventType = eventType;
    this.body = body;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEndpointId() {
    return endpointId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getBody() {
    return body;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
