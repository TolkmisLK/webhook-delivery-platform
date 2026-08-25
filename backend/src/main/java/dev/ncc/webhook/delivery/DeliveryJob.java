package dev.ncc.webhook.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_job")
public class DeliveryJob {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "endpoint_id", nullable = false)
  private UUID endpointId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DeliveryStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 160)
  private String lockedBy;

  @Column(name = "last_status_code")
  private Integer lastStatusCode;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected DeliveryJob() {}

  private DeliveryJob(
      UUID id, UUID eventId, UUID endpointId, DeliveryStatus status, int maxAttempts, Instant now) {
    this.id = id;
    this.eventId = eventId;
    this.endpointId = endpointId;
    this.status = status;
    this.maxAttempts = maxAttempts;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static DeliveryJob pending(
      UUID id, UUID eventId, UUID endpointId, int maxAttempts, Instant now) {
    return new DeliveryJob(id, eventId, endpointId, DeliveryStatus.PENDING, maxAttempts, now);
  }

  public void claim(String workerId, Instant now) {
    status = DeliveryStatus.PROCESSING;
    lockedBy = workerId;
    lockedAt = now;
    updatedAt = now;
  }

  public void succeed(int attempt, int statusCode, Instant now) {
    status = DeliveryStatus.SUCCEEDED;
    attemptCount = attempt;
    lastStatusCode = statusCode;
    lastError = null;
    clearLease();
    updatedAt = now;
  }

  public void failPermanently(int attempt, Integer statusCode, String error, Instant now) {
    status = DeliveryStatus.DEAD;
    attemptCount = attempt;
    lastStatusCode = statusCode;
    lastError = truncate(error, 1000);
    clearLease();
    updatedAt = now;
  }

  public void scheduleRetry(
      int attempt, Integer statusCode, String error, Instant nextAttemptAt, Instant now) {
    status = DeliveryStatus.RETRY_SCHEDULED;
    attemptCount = attempt;
    lastStatusCode = statusCode;
    lastError = truncate(error, 1000);
    this.nextAttemptAt = nextAttemptAt;
    clearLease();
    updatedAt = now;
  }

  public void replay(Instant now, int additionalAttempts) {
    if (status == DeliveryStatus.PROCESSING) {
      throw new IllegalArgumentException("A processing delivery cannot be replayed");
    }
    status = DeliveryStatus.PENDING;
    maxAttempts = attemptCount + additionalAttempts;
    nextAttemptAt = now;
    lastStatusCode = null;
    lastError = null;
    clearLease();
    updatedAt = now;
  }

  private void clearLease() {
    lockedAt = null;
    lockedBy = null;
  }

  private String truncate(String value, int maximum) {
    if (value == null || value.length() <= maximum) {
      return value;
    }
    return value.substring(0, maximum);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getEndpointId() {
    return endpointId;
  }

  public DeliveryStatus getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Integer getLastStatusCode() {
    return lastStatusCode;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
