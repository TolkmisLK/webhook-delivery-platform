package dev.ncc.webhook.delivery;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class DeliveryUpdates {

  private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(error -> emitters.remove(emitter));
    try {
      emitter.send(
          SseEmitter.event().name("connected").data(new Update(null, "CONNECTED", Instant.now())));
    } catch (IOException exception) {
      emitters.remove(emitter);
      emitter.completeWithError(exception);
    }
    return emitter;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void publishCommitted(DeliveryStateChanged event) {
    Update update = new Update(event.jobId(), event.status().name(), Instant.now());
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("delivery").data(update));
      } catch (IOException exception) {
        emitters.remove(emitter);
        emitter.complete();
      }
    }
  }

  public record Update(UUID deliveryId, String status, Instant occurredAt) {}
}
