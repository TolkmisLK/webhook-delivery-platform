package dev.ncc.webhook.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
  List<DeliveryAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}
