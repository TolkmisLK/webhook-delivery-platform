package dev.ncc.webhook.delivery;

import dev.ncc.webhook.common.NotFoundException;
import dev.ncc.webhook.config.SecretCipher;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import dev.ncc.webhook.event.WebhookEvent;
import dev.ncc.webhook.event.WebhookEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryContextService {

  private final DeliveryJobRepository jobRepository;
  private final WebhookEventRepository eventRepository;
  private final UrlSafetyPolicy urlSafetyPolicy;
  private final SecretCipher secretCipher;

  DeliveryContextService(
      DeliveryJobRepository jobRepository,
      WebhookEventRepository eventRepository,
      UrlSafetyPolicy urlSafetyPolicy,
      SecretCipher secretCipher) {
    this.jobRepository = jobRepository;
    this.eventRepository = eventRepository;
    this.urlSafetyPolicy = urlSafetyPolicy;
    this.secretCipher = secretCipher;
  }

  @Transactional(readOnly = true)
  DeliveryContext load(UUID jobId) {
    DeliveryJob job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new NotFoundException("Delivery job was not found"));
    if (job.getStatus() != DeliveryStatus.PROCESSING) {
      throw new IllegalStateException("Delivery job is not claimed");
    }
    WebhookEvent event =
        eventRepository
            .findById(job.getEventId())
            .orElseThrow(() -> new IllegalStateException("Webhook event is missing"));
    String safeUrl = urlSafetyPolicy.validate(job.getTargetUrl()).toASCIIString();
    return new DeliveryContext(
        job.getId(),
        event.getId(),
        event.getEventType(),
        safeUrl,
        secretCipher.decrypt(job.getEncryptedSecret()),
        event.getBody(),
        event.getCreatedAt(),
        job.getAttemptCount(),
        job.getMaxAttempts());
  }
}
