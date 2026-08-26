package dev.ncc.webhook.delivery;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, UUID> {

  Optional<DeliveryJob> findByEventId(UUID eventId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select job from DeliveryJob job where job.id = :id")
  Optional<DeliveryJob> findByIdForUpdate(@Param("id") UUID id);

  long countByStatus(DeliveryStatus status);

  List<DeliveryJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @Query(
      """
      select new dev.ncc.webhook.delivery.DeliveryStatusCount(job.status, count(job))
      from DeliveryJob job
      group by job.status
      """)
  List<DeliveryStatusCount> countByStatusGrouped();

  @Query(
      """
      select min(job.nextAttemptAt)
      from DeliveryJob job
      where job.status in :statuses
        and job.nextAttemptAt <= :now
      """)
  Optional<Instant> findOldestDueAt(
      @Param("statuses") List<DeliveryStatus> statuses, @Param("now") Instant now);

  @Query(
      """
      select min(job.lockedAt)
      from DeliveryJob job
      where job.status = :status
        and job.lockedAt < :stale_before
      """)
  Optional<Instant> findOldestStaleLockAt(
      @Param("status") DeliveryStatus status, @Param("stale_before") Instant staleBefore);

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
