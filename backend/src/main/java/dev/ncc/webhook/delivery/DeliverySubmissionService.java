package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import dev.ncc.webhook.event.DeliverySubmission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliverySubmissionService implements DeliverySubmission {

  private final DeliveryJobRepository repository;
  private final DeliveryProperties properties;
  private final ApplicationEventPublisher events;

  DeliverySubmissionService(
      DeliveryJobRepository repository,
      DeliveryProperties properties,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.properties = properties;
    this.events = events;
  }

  @Override
  @Transactional
  public AcceptedDelivery ensureScheduled(UUID eventId, UUID endpointId, Instant acceptedAt) {
    return repository
        .findByEventId(eventId)
        .map(this::accepted)
        .orElseGet(
            () -> {
              DeliveryJob job =
                  DeliveryJob.pending(
                      UUID.randomUUID(),
                      eventId,
                      endpointId,
                      properties.getMaxAttempts(),
                      acceptedAt);
              repository.save(job);
              events.publishEvent(
                  new DeliveryStateChanged(
                      job.getId(),
                      null,
                      DeliveryStatus.PENDING,
                      DeliveryStateChangeSource.ACCEPTED));
              return accepted(job);
            });
  }

  private AcceptedDelivery accepted(DeliveryJob job) {
    return new AcceptedDelivery(job.getId(), job.getStatus().name());
  }
}
