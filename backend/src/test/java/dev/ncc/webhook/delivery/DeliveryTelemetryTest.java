package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DeliveryTelemetryTest {

  @Test
  void recordsAttemptsAndDurationsByCommittedOutcome() {
    var registry = new SimpleMeterRegistry();
    var telemetry = new DeliveryTelemetry(registry);

    telemetry.record(
        new DeliveryAttemptCompleted(
            UUID.randomUUID(), 2, DeliveryStatus.RETRY_SCHEDULED, 503, 125));

    assertThat(
            registry
                .get("webhook.delivery.attempts")
                .tag("outcome", "retry_scheduled")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get("webhook.delivery.duration")
                .tag("outcome", "retry_scheduled")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
        .isEqualTo(125);
  }
}
