package dev.ncc.webhook.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
  Optional<WebhookEvent> findByEndpointIdAndIdempotencyKey(UUID endpointId, String idempotencyKey);
}
