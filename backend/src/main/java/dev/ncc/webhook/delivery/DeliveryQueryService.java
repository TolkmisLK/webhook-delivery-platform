package dev.ncc.webhook.delivery;

import dev.ncc.webhook.common.NotFoundException;
import dev.ncc.webhook.config.DeliveryProperties;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryAttemptResponse;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryDetailResponse;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryResponse;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryStats;
import dev.ncc.webhook.endpoint.WebhookEndpointRepository;
import dev.ncc.webhook.event.WebhookEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryQueryService {

  private final DeliveryJobRepository jobRepository;
  private final DeliveryAttemptRepository attemptRepository;
  private final WebhookEventRepository eventRepository;
  private final WebhookEndpointRepository endpointRepository;
  private final ApplicationEventPublisher events;
  private final DeliveryProperties properties;
  private final Clock clock;

  DeliveryQueryService(
      DeliveryJobRepository jobRepository,
      DeliveryAttemptRepository attemptRepository,
      WebhookEventRepository eventRepository,
      WebhookEndpointRepository endpointRepository,
      ApplicationEventPublisher events,
      DeliveryProperties properties,
      Clock clock) {
    this.jobRepository = jobRepository;
    this.attemptRepository = attemptRepository;
    this.eventRepository = eventRepository;
    this.endpointRepository = endpointRepository;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  List<DeliveryResponse> recent(int requestedLimit) {
    int limit = Math.min(Math.max(requestedLimit, 1), 100);
    return jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  DeliveryStats stats() {
    Map<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
    Arrays.stream(DeliveryStatus.values())
        .forEach(status -> counts.put(status, jobRepository.countByStatus(status)));
    long total = counts.values().stream().mapToLong(Long::longValue).sum();
    return new DeliveryStats(total, counts);
  }

  @Transactional(readOnly = true)
  DeliveryDetailResponse detail(UUID id) {
    DeliveryJob job =
        jobRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    List<DeliveryAttemptResponse> attempts =
        attemptRepository.findByJobIdOrderByAttemptNumberAsc(id).stream()
            .map(this::toAttemptResponse)
            .toList();
    return new DeliveryDetailResponse(toResponse(job), attempts);
  }

  @Transactional
  DeliveryResponse replay(UUID id) {
    DeliveryJob job =
        jobRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    DeliveryStatus previousStatus = job.getStatus();
    job.replay(Instant.now(clock), properties.getMaxAttempts());
    events.publishEvent(
        new DeliveryStateChanged(
            id, previousStatus, DeliveryStatus.PENDING, DeliveryStateChangeSource.MANUAL_REPLAY));
    return toResponse(job);
  }

  @Transactional
  DeliveryResponse cancel(UUID id) {
    DeliveryJob job =
        jobRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    DeliveryStatus previousStatus = job.getStatus();
    if (job.cancel(Instant.now(clock))) {
      events.publishEvent(
          new DeliveryStateChanged(
              id,
              previousStatus,
              DeliveryStatus.CANCELED,
              DeliveryStateChangeSource.MANUAL_CANCEL));
    }
    return toResponse(job);
  }

  private DeliveryResponse toResponse(DeliveryJob job) {
    var event =
        eventRepository
            .findById(job.getEventId())
            .orElseThrow(() -> new IllegalStateException("Webhook event is missing"));
    var endpoint =
        endpointRepository
            .findById(job.getEndpointId())
            .orElseThrow(() -> new IllegalStateException("Webhook endpoint is missing"));
    return new DeliveryResponse(
        job.getId(),
        event.getId(),
        event.getEventType(),
        endpoint.getName(),
        endpoint.getUrl(),
        job.getStatus(),
        job.getAttemptCount(),
        job.getMaxAttempts(),
        job.getNextAttemptAt(),
        job.getLastStatusCode(),
        job.getLastError(),
        job.getCreatedAt(),
        job.getUpdatedAt());
  }

  private DeliveryAttemptResponse toAttemptResponse(DeliveryAttempt attempt) {
    return new DeliveryAttemptResponse(
        attempt.getAttemptNumber(),
        attempt.getOutcome(),
        attempt.getStatusCode(),
        attempt.getErrorMessage(),
        attempt.getResponseExcerpt(),
        attempt.getDurationMs(),
        attempt.getStartedAt(),
        attempt.getFinishedAt());
  }
}
