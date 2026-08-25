package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import dev.ncc.webhook.event.DeliverySubmission;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliverySubmissionService implements DeliverySubmission {

  private final DeliveryJobRepository repository;
  private final DeliveryProperties properties;
  private final DeliveryUpdates updates;

  DeliverySubmissionService(
      DeliveryJobRepository repository, DeliveryProperties properties, DeliveryUpdates updates) {
    this.repository = repository;
    this.properties = properties;
    this.updates = updates;
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
              updates.publish(job.getId(), DeliveryStatus.PENDING.name());
              return accepted(job);
            });
  }

  private AcceptedDelivery accepted(DeliveryJob job) {
    return new AcceptedDelivery(job.getId(), job.getStatus().name());
  }
}
