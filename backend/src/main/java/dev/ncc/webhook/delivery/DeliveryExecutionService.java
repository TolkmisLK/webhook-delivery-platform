package dev.ncc.webhook.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class DeliveryExecutionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryExecutionService.class);
  private final DeliveryContextService contextService;
  private final WebhookHttpClient httpClient;
  private final DeliveryOutcomeService outcomeService;
  private final Clock clock;

  DeliveryExecutionService(
      DeliveryContextService contextService,
      WebhookHttpClient httpClient,
      DeliveryOutcomeService outcomeService,
      Clock clock) {
    this.contextService = contextService;
    this.httpClient = httpClient;
    this.outcomeService = outcomeService;
    this.clock = clock;
  }

  void execute(UUID jobId) {
    Instant startedAt = Instant.now(clock);
    try {
      DeliveryContext context = contextService.load(jobId);
      DeliveryResult result = httpClient.deliver(context);
      outcomeService.complete(context, result, startedAt);
    } catch (RuntimeException exception) {
      LOGGER.error("Delivery execution failed for job {}", jobId, exception);
      try {
        outcomeService.completeUnexpectedFailure(jobId, exception, startedAt);
      } catch (RuntimeException recoveryException) {
        LOGGER.error("Could not persist delivery failure for job {}", jobId, recoveryException);
      }
    }
  }
}
