package dev.ncc.webhook.endpoint;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select endpoint from WebhookEndpoint endpoint where endpoint.id = :id")
  Optional<WebhookEndpoint> findByIdForUpdate(@Param("id") UUID id);
}
