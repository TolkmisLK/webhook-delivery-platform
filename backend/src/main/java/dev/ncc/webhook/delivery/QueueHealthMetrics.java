package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class QueueHealthMetrics implements MeterBinder {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueueHealthMetrics.class);
  private static final List<DeliveryStatus> RUNNABLE_STATUSES =
      List.of(DeliveryStatus.PENDING, DeliveryStatus.RETRY_SCHEDULED);

  private final DeliveryJobRepository repository;
  private final Clock clock;
  private final Duration leaseTimeout;
  private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

  QueueHealthMetrics(DeliveryJobRepository repository, Clock clock, DeliveryProperties properties) {
    this.repository = repository;
    this.clock = clock;
    this.leaseTimeout = properties.getLeaseTimeout();
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (DeliveryStatus status : DeliveryStatus.values()) {
      Gauge.builder("webhook.delivery.jobs", snapshot, value -> value.get().count(status))
          .description("Durable delivery jobs by bounded lifecycle status")
          .tag("status", status.name().toLowerCase(Locale.ROOT))
          .register(registry);
    }
    Gauge.builder(
            "webhook.delivery.oldest.runnable.age",
            snapshot,
            value -> value.get().oldestRunnableAgeSeconds())
        .description("Age of the oldest currently runnable durable delivery job")
        .baseUnit("seconds")
        .register(registry);
  }

  @EventListener(ApplicationReadyEvent.class)
  void refreshAtStartup() {
    refreshSafely();
  }

  @Scheduled(fixedDelayString = "${app.delivery.metrics-refresh-interval:5s}")
  void refreshSafely() {
    try {
      refresh();
    } catch (RuntimeException exception) {
      LOGGER.warn("queue_health_metrics_refresh_failed", exception);
    }
  }

  void refresh() {
    Instant now = Instant.now(clock);
    EnumMap<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
    for (DeliveryStatus status : DeliveryStatus.values()) {
      counts.put(status, 0L);
    }
    for (DeliveryStatusCount result : repository.countByStatusGrouped()) {
      counts.put(result.status(), result.count());
    }

    Optional<Instant> dueAt = repository.findOldestDueAt(RUNNABLE_STATUSES, now);
    Optional<Instant> staleAt =
        repository
            .findOldestStaleLockAt(DeliveryStatus.PROCESSING, now.minus(leaseTimeout))
            .map(lockedAt -> lockedAt.plus(leaseTimeout));
    Instant oldestRunnableAt = earlier(dueAt.orElse(null), staleAt.orElse(null));
    double oldestRunnableAgeSeconds = ageSeconds(oldestRunnableAt, now);
    snapshot.set(new Snapshot(Map.copyOf(counts), oldestRunnableAgeSeconds));
  }

  private Instant earlier(Instant first, Instant second) {
    if (first == null) {
      return second;
    }
    if (second == null || first.isBefore(second)) {
      return first;
    }
    return second;
  }

  private double ageSeconds(Instant startedAt, Instant now) {
    if (startedAt == null) {
      return 0;
    }
    return Math.max(0, Duration.between(startedAt, now).toMillis() / 1000.0);
  }

  private record Snapshot(Map<DeliveryStatus, Long> counts, double oldestRunnableAgeSeconds) {

    static Snapshot empty() {
      EnumMap<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
      for (DeliveryStatus status : DeliveryStatus.values()) {
        counts.put(status, 0L);
      }
      return new Snapshot(Map.copyOf(counts), 0);
    }

    long count(DeliveryStatus status) {
      return counts.getOrDefault(status, 0L);
    }
  }
}
