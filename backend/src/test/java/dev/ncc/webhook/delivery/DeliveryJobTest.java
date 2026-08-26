package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryJobTest {

  private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

  @Test
  void preservesAttemptSequenceWhenADeadDeliveryIsReplayed() {
    DeliveryJob job =
        DeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, NOW);
    job.claim("worker-a", NOW);
    job.failPermanently(3, 400, "invalid target response", NOW);

    job.replay(NOW.plusSeconds(1), 3);

    assertThat(job.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(job.getAttemptCount()).isEqualTo(3);
    assertThat(job.getMaxAttempts()).isEqualTo(6);
    assertThat(job.getLastError()).isNull();
  }

  @Test
  void refusesToReplayAJobThatIsBeingProcessed() {
    DeliveryJob job =
        DeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, NOW);
    job.claim("worker-a", NOW);

    assertThatThrownBy(() -> job.replay(NOW, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processing");
  }

  @Test
  void cancelsQueuedJobsAndKeepsRepeatedCancellationIdempotent() {
    DeliveryJob job =
        DeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, NOW);

    assertThat(job.cancel(NOW.plusSeconds(1))).isTrue();
    assertThat(job.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
    assertThat(job.cancel(NOW.plusSeconds(2))).isFalse();
    assertThat(job.getStatus()).isEqualTo(DeliveryStatus.CANCELED);

    job.replay(NOW.plusSeconds(3), 3);
    assertThat(job.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(job.getMaxAttempts()).isEqualTo(3);
  }

  @Test
  void cancelsAJobWaitingForRetryWithoutDiscardingDiagnostics() {
    DeliveryJob job =
        DeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, NOW);
    job.claim("worker-a", NOW);
    job.scheduleRetry(1, 503, "temporary failure", NOW.plusSeconds(30), NOW);

    assertThat(job.cancel(NOW.plusSeconds(1))).isTrue();

    assertThat(job.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
    assertThat(job.getAttemptCount()).isEqualTo(1);
    assertThat(job.getLastStatusCode()).isEqualTo(503);
    assertThat(job.getLastError()).isEqualTo("temporary failure");
  }

  @Test
  void refusesToCancelAJobThatIsBeingProcessed() {
    DeliveryJob job =
        DeliveryJob.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, NOW);
    job.claim("worker-a", NOW);

    assertThatThrownBy(() -> job.cancel(NOW.plusSeconds(1)))
        .isInstanceOf(dev.ncc.webhook.common.DeliveryStateConflictException.class)
        .hasMessageContaining("PROCESSING");
  }
}
