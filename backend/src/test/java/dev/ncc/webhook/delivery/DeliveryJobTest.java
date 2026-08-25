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
}
