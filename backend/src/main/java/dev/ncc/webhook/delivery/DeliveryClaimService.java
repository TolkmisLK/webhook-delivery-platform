package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryClaimService {

  private final DeliveryJobRepository repository;
  private final DeliveryProperties properties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  DeliveryClaimService(
      DeliveryJobRepository repository,
      DeliveryProperties properties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  List<UUID> claimBatch(int availableSlots) {
    Instant now = Instant.now(clock);
    Instant staleBefore = now.minus(properties.getLeaseTimeout());
    int limit = Math.min(Math.max(availableSlots, 0), properties.getBatchSize());
    if (limit == 0) {
      return List.of();
    }
    List<DeliveryJob> jobs = repository.findClaimable(now, staleBefore, limit);
    jobs.forEach(
        job -> {
          DeliveryStatus previousStatus = job.getStatus();
          job.claim(properties.getWorkerId(), now);
          DeliveryStateChangeSource source =
              previousStatus == DeliveryStatus.PROCESSING
                  ? DeliveryStateChangeSource.WORKER_RECLAIM
                  : DeliveryStateChangeSource.WORKER_CLAIM;
          events.publishEvent(
              new DeliveryStateChanged(
                  job.getId(), previousStatus, DeliveryStatus.PROCESSING, source));
        });
    return jobs.stream().map(DeliveryJob::getId).toList();
  }
}
