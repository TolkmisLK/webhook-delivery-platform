package dev.ncc.webhook.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, UUID> {

  Optional<DeliveryJob> findByEventId(UUID eventId);

  long countByStatus(DeliveryStatus status);

  List<DeliveryJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query(
      value =
          """
          select *
          from delivery_job
          where (
              status in ('PENDING', 'RETRY_SCHEDULED')
              and next_attempt_at <= :now
          ) or (
              status = 'PROCESSING'
              and locked_at < :stale_before
          )
          order by next_attempt_at asc, created_at asc
          for update skip locked
          limit :batch_size
          """,
      nativeQuery = true)
  List<DeliveryJob> findClaimable(
      @Param("now") Instant now,
      @Param("stale_before") Instant staleBefore,
      @Param("batch_size") int batchSize);
}
