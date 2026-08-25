package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryClaimService {

  private final DeliveryJobRepository repository;
  private final DeliveryProperties properties;
  private final Clock clock;

  DeliveryClaimService(
      DeliveryJobRepository repository, DeliveryProperties properties, Clock clock) {
    this.repository = repository;
    this.properties = properties;
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
    jobs.forEach(job -> job.claim(properties.getWorkerId(), now));
    return jobs.stream().map(DeliveryJob::getId).toList();
  }
}
