package dev.ncc.webhook.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttempt {

  @Id private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DeliveryStatus outcome;

  @Column(name = "status_code")
  private Integer statusCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "response_excerpt", length = 4096)
  private String responseExcerpt;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = false)
  private Instant finishedAt;

  protected DeliveryAttempt() {}

  DeliveryAttempt(
      UUID id,
      UUID jobId,
      int attemptNumber,
      DeliveryStatus outcome,
      Integer statusCode,
      String errorMessage,
      String responseExcerpt,
      long durationMs,
      Instant startedAt,
      Instant finishedAt) {
    this.id = id;
    this.jobId = jobId;
    this.attemptNumber = attemptNumber;
    this.outcome = outcome;
    this.statusCode = statusCode;
    this.errorMessage = truncate(errorMessage, 1000);
    this.responseExcerpt = truncate(responseExcerpt, 4096);
    this.durationMs = durationMs;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public DeliveryStatus getOutcome() {
    return outcome;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getResponseExcerpt() {
    return responseExcerpt;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  private String truncate(String value, int maximum) {
    if (value == null || value.length() <= maximum) {
      return value;
    }
    return value.substring(0, maximum);
  }
}
