package dev.ncc.webhook.delivery;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class DeliveryTelemetry {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryTelemetry.class);
  private final MeterRegistry registry;

  DeliveryTelemetry(MeterRegistry registry) {
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void record(DeliveryAttemptCompleted event) {
    String outcome = event.outcome().name().toLowerCase(Locale.ROOT);
    registry.counter("webhook.delivery.attempts", "outcome", outcome).increment();
    registry
        .timer("webhook.delivery.duration", "outcome", outcome)
        .record(event.durationMs(), TimeUnit.MILLISECONDS);
    LOGGER.info(
        "delivery_attempt_completed jobId={} attempt={} outcome={} statusCode={} durationMs={}",
        event.jobId(),
        event.attemptNumber(),
        event.outcome(),
        event.statusCode(),
        event.durationMs());
  }
}
