package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DeliveryWorker {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryWorker.class);
  private final DeliveryClaimService claimService;
  private final DeliveryExecutionService executionService;
  private final ExecutorService executor;
  private final Semaphore permits;

  DeliveryWorker(
      DeliveryClaimService claimService,
      DeliveryExecutionService executionService,
      DeliveryProperties properties) {
    this.claimService = claimService;
    this.executionService = executionService;
    this.executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("delivery-worker-", 0).factory());
    this.permits = new Semaphore(properties.getWorkerConcurrency());
  }

  @Scheduled(fixedDelayString = "${app.delivery.poll-interval:1s}")
  synchronized void poll() {
    int reservedSlots = permits.drainPermits();
    if (reservedSlots == 0) {
      return;
    }

    List<UUID> jobIds;
    try {
      jobIds = claimService.claimBatch(reservedSlots);
    } catch (RuntimeException exception) {
      permits.release(reservedSlots);
      throw exception;
    }
    permits.release(reservedSlots - jobIds.size());

    for (UUID jobId : jobIds) {
      try {
        executor.submit(
            () -> {
              try {
                executionService.execute(jobId);
              } finally {
                permits.release();
              }
            });
      } catch (RuntimeException exception) {
        LOGGER.warn(
            "Executor rejected delivery job {}; running it on the scheduler thread",
            jobId,
            exception);
        try {
          executionService.execute(jobId);
        } finally {
          permits.release();
        }
      }
    }
  }

  @PreDestroy
  void stop() {
    executor.shutdown();
  }
}
