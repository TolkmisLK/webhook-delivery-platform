package dev.ncc.webhook.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class EndpointLifecycleLog {

  private static final Logger logger = LoggerFactory.getLogger(EndpointLifecycleLog.class);

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void record(EndpointStatusChanged event) {
    logger.info(
        "endpoint_status_changed endpointId={} active={} version={}",
        event.endpointId(),
        event.active(),
        event.version());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void record(EndpointSecretRotated event) {
    logger.info(
        "endpoint_secret_rotated endpointId={} version={} rotatedAt={}",
        event.endpointId(),
        event.version(),
        event.rotatedAt());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void record(EndpointConfigurationChanged event) {
    logger.info(
        "endpoint_configuration_changed endpointId={} version={} nameChanged={} urlChanged={}"
            + " changedAt={}",
        event.endpointId(),
        event.version(),
        event.nameChanged(),
        event.urlChanged(),
        event.changedAt());
  }
}
