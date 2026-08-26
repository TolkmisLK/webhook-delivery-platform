package dev.ncc.webhook.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class DeliveryLifecycleLog {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryLifecycleLog.class);

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void recordOperatorAction(DeliveryStateChanged event) {
    if (event.source() != DeliveryStateChangeSource.MANUAL_REPLAY
        && event.source() != DeliveryStateChangeSource.MANUAL_CANCEL) {
      return;
    }
    LOGGER.info(
        "delivery_operator_action jobId={} action={} previousStatus={} status={}",
        event.jobId(),
        event.source(),
        event.previousStatus(),
        event.status());
  }
}
