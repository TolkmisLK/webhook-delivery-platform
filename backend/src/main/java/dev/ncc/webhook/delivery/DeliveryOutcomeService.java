package dev.ncc.webhook.delivery;

import dev.ncc.webhook.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryOutcomeService {

  private final DeliveryJobRepository jobRepository;
  private final DeliveryAttemptRepository attemptRepository;
  private final RetryPolicy retryPolicy;
  private final DeliveryUpdates updates;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  DeliveryOutcomeService(
      DeliveryJobRepository jobRepository,
      DeliveryAttemptRepository attemptRepository,
      RetryPolicy retryPolicy,
      DeliveryUpdates updates,
      ApplicationEventPublisher events,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.attemptRepository = attemptRepository;
    this.retryPolicy = retryPolicy;
    this.updates = updates;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  void complete(DeliveryContext context, DeliveryResult result, Instant startedAt) {
    DeliveryJob job =
        jobRepository
            .findById(context.jobId())
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    int attempt = context.completedAttempts() + 1;
    Instant now = Instant.now(clock);
    DeliveryStatus outcome;

    if (result.successful()) {
      job.succeed(attempt, result.statusCode(), now);
      outcome = DeliveryStatus.SUCCEEDED;
    } else if (!result.retryable() || attempt >= context.maxAttempts()) {
      job.failPermanently(attempt, result.statusCode(), result.error(), now);
      outcome = DeliveryStatus.DEAD;
    } else {
      job.scheduleRetry(
          attempt,
          result.statusCode(),
          result.error(),
          now.plus(retryPolicy.delayAfter(attempt)),
          now);
      outcome = DeliveryStatus.RETRY_SCHEDULED;
    }

    attemptRepository.save(
        new DeliveryAttempt(
            UUID.randomUUID(),
            job.getId(),
            attempt,
            outcome,
            result.statusCode(),
            result.error(),
            result.responseExcerpt(),
            result.durationMs(),
            startedAt,
            now));
    events.publishEvent(
        new DeliveryAttemptCompleted(
            job.getId(), attempt, outcome, result.statusCode(), result.durationMs()));
    updates.publish(job.getId(), outcome.name());
  }

  @Transactional
  void completeUnexpectedFailure(UUID jobId, RuntimeException exception, Instant startedAt) {
    DeliveryJob job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    if (job.getStatus() != DeliveryStatus.PROCESSING) {
      return;
    }

    int attempt = job.getAttemptCount() + 1;
    Instant now = Instant.now(clock);
    String error = "Internal delivery error: " + exception.getClass().getSimpleName();
    DeliveryStatus outcome;
    if (attempt >= job.getMaxAttempts()) {
      job.failPermanently(attempt, null, error, now);
      outcome = DeliveryStatus.DEAD;
    } else {
      job.scheduleRetry(attempt, null, error, now.plus(retryPolicy.delayAfter(attempt)), now);
      outcome = DeliveryStatus.RETRY_SCHEDULED;
    }

    long durationMs = Math.max(0, java.time.Duration.between(startedAt, now).toMillis());
    attemptRepository.save(
        new DeliveryAttempt(
            UUID.randomUUID(),
            job.getId(),
            attempt,
            outcome,
            null,
            error,
            null,
            durationMs,
            startedAt,
            now));
    events.publishEvent(
        new DeliveryAttemptCompleted(job.getId(), attempt, outcome, null, durationMs));
    updates.publish(job.getId(), outcome.name());
  }
}
