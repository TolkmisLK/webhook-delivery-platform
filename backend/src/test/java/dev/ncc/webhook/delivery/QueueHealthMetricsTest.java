package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.ncc.webhook.config.DeliveryProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueueHealthMetricsTest {

  private static final Instant NOW = Instant.parse("2026-08-25T04:00:00Z");

  @Test
  void publishesBoundedStatusCountsAndOldestRunnableAge() {
    DeliveryJobRepository repository = mock(DeliveryJobRepository.class);
    when(repository.countByStatusGrouped())
        .thenReturn(
            List.of(
                new DeliveryStatusCount(DeliveryStatus.PENDING, 3),
                new DeliveryStatusCount(DeliveryStatus.DEAD, 1)));
    when(repository.findOldestDueAt(
            List.of(DeliveryStatus.PENDING, DeliveryStatus.RETRY_SCHEDULED), NOW))
        .thenReturn(Optional.of(NOW.minusSeconds(42)));
    when(repository.findOldestStaleLockAt(DeliveryStatus.PROCESSING, NOW.minusSeconds(30)))
        .thenReturn(Optional.of(NOW.minusSeconds(35)));

    var registry = new SimpleMeterRegistry();
    var metrics =
        new QueueHealthMetrics(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), new DeliveryProperties());
    metrics.bindTo(registry);
    metrics.refresh();

    assertThat(registry.find("webhook.delivery.jobs").gauges())
        .hasSize(5)
        .allSatisfy(gauge -> assertThat(gauge.getId().getTags()).hasSize(1))
        .extracting(gauge -> gauge.getId().getTag("status"))
        .containsExactlyInAnyOrder("pending", "processing", "retry_scheduled", "succeeded", "dead");
    assertThat(registry.get("webhook.delivery.jobs").tag("status", "pending").gauge().value())
        .isEqualTo(3);
    assertThat(registry.get("webhook.delivery.jobs").tag("status", "processing").gauge().value())
        .isZero();
    assertThat(registry.get("webhook.delivery.oldest.runnable.age").gauge().value()).isEqualTo(42);
    assertThat(registry.get("webhook.delivery.oldest.runnable.age").gauge().getId().getTags())
        .isEmpty();
  }

  @Test
  void reportsZeroRunnableAgeWhenTheQueueHasNoClaimableWork() {
    DeliveryJobRepository repository = mock(DeliveryJobRepository.class);
    when(repository.countByStatusGrouped()).thenReturn(List.of());
    when(repository.findOldestDueAt(
            List.of(DeliveryStatus.PENDING, DeliveryStatus.RETRY_SCHEDULED), NOW))
        .thenReturn(Optional.empty());
    when(repository.findOldestStaleLockAt(DeliveryStatus.PROCESSING, NOW.minusSeconds(30)))
        .thenReturn(Optional.empty());

    var registry = new SimpleMeterRegistry();
    var metrics =
        new QueueHealthMetrics(
            repository, Clock.fixed(NOW, ZoneOffset.UTC), new DeliveryProperties());
    metrics.bindTo(registry);
    metrics.refresh();

    assertThat(registry.get("webhook.delivery.oldest.runnable.age").gauge().value()).isZero();
  }
}
