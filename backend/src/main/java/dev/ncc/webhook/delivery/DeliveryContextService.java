package dev.ncc.webhook.delivery;

import dev.ncc.webhook.common.NotFoundException;
import dev.ncc.webhook.config.SecretCipher;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import dev.ncc.webhook.endpoint.WebhookEndpoint;
import dev.ncc.webhook.endpoint.WebhookEndpointRepository;
import dev.ncc.webhook.event.WebhookEvent;
import dev.ncc.webhook.event.WebhookEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeliveryContextService {

  private final DeliveryJobRepository jobRepository;
  private final WebhookEventRepository eventRepository;
  private final WebhookEndpointRepository endpointRepository;
  private final UrlSafetyPolicy urlSafetyPolicy;
  private final SecretCipher secretCipher;

  DeliveryContextService(
      DeliveryJobRepository jobRepository,
      WebhookEventRepository eventRepository,
      WebhookEndpointRepository endpointRepository,
      UrlSafetyPolicy urlSafetyPolicy,
      SecretCipher secretCipher) {
    this.jobRepository = jobRepository;
    this.eventRepository = eventRepository;
    this.endpointRepository = endpointRepository;
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
    WebhookEndpoint endpoint =
        endpointRepository
            .findById(job.getEndpointId())
            .orElseThrow(() -> new IllegalStateException("Webhook endpoint is missing"));

    String safeUrl = urlSafetyPolicy.validate(endpoint.getUrl()).toASCIIString();
    return new DeliveryContext(
        job.getId(),
        event.getId(),
        event.getEventType(),
        safeUrl,
        secretCipher.decrypt(endpoint.getEncryptedSecret()),
        event.getBody(),
        event.getCreatedAt(),
        job.getAttemptCount(),
        job.getMaxAttempts());
  }
}
