package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.ncc.webhook.config.DeliveryProperties;
import dev.ncc.webhook.endpoint.WebhookEndpoint;
import dev.ncc.webhook.endpoint.WebhookEndpointRepository;
import dev.ncc.webhook.event.WebhookEvent;
import dev.ncc.webhook.event.WebhookEventRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class DeliveryStateEventsTest {

  private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String TARGET_URL = "https://example.com/hooks";
  private static final String ENCRYPTED_SECRET = "encrypted-secret";

  @Test
  void publishesOneManualCancelEventWhenCancellationIsRepeated() {
    DeliveryJobRepository jobs = mock(DeliveryJobRepository.class);
    DeliveryAttemptRepository attempts = mock(DeliveryAttemptRepository.class);
    WebhookEventRepository webhookEvents = mock(WebhookEventRepository.class);
    WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    DeliveryJob job = pendingJob();
    WebhookEvent event = mock(WebhookEvent.class);
    WebhookEndpoint endpoint = mock(WebhookEndpoint.class);
    when(jobs.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
    when(webhookEvents.findById(job.getEventId())).thenReturn(Optional.of(event));
    when(endpoints.findById(job.getEndpointId())).thenReturn(Optional.of(endpoint));
    when(event.getId()).thenReturn(job.getEventId());

    List<Object> published = new ArrayList<>();
    ApplicationEventPublisher publisher = published::add;
    DeliveryQueryService service =
        new DeliveryQueryService(
            jobs, attempts, webhookEvents, endpoints, publisher, new DeliveryProperties(), CLOCK);

    service.cancel(job.getId());
    service.cancel(job.getId());

    assertThat(published)
        .containsExactly(
            new DeliveryStateChanged(
                job.getId(),
                DeliveryStatus.PENDING,
                DeliveryStatus.CANCELED,
                DeliveryStateChangeSource.MANUAL_CANCEL));
  }

  @Test
  void distinguishesWorkerClaimsFromStaleLeaseRecovery() {
    DeliveryJobRepository jobs = mock(DeliveryJobRepository.class);
    DeliveryJob pending = pendingJob();
    DeliveryJob stale = pendingJob();
    stale.claim("stale-worker", NOW.minusSeconds(60));
    when(jobs.findClaimable(any(), any(), anyInt())).thenReturn(List.of(pending, stale));

    List<Object> published = new ArrayList<>();
    DeliveryClaimService service =
        new DeliveryClaimService(jobs, new DeliveryProperties(), published::add, CLOCK);

    service.claimBatch(2);

    assertThat(published)
        .containsExactly(
            new DeliveryStateChanged(
                pending.getId(),
                DeliveryStatus.PENDING,
                DeliveryStatus.PROCESSING,
                DeliveryStateChangeSource.WORKER_CLAIM),
            new DeliveryStateChanged(
                stale.getId(),
                DeliveryStatus.PROCESSING,
                DeliveryStatus.PROCESSING,
                DeliveryStateChangeSource.WORKER_RECLAIM));
  }

  @Test
  void sseAndOperatorAuditListenersRunOnlyAfterCommit() throws Exception {
    assertAfterCommit(
        DeliveryUpdates.class.getDeclaredMethod("publishCommitted", DeliveryStateChanged.class));
    assertAfterCommit(
        DeliveryLifecycleLog.class.getDeclaredMethod(
            "recordOperatorAction", DeliveryStateChanged.class));
  }

  private void assertAfterCommit(Method method) {
    TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(listener.fallbackExecution()).isFalse();
  }

  private DeliveryJob pendingJob() {
    return DeliveryJob.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        TARGET_URL,
        ENCRYPTED_SECRET,
        3,
        NOW);
  }
}
