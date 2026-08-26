package dev.ncc.webhook.endpoint;

import dev.ncc.webhook.common.NotFoundException;
import dev.ncc.webhook.common.VersionConflictException;
import dev.ncc.webhook.config.SecretCipher;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import dev.ncc.webhook.endpoint.EndpointDtos.CreateEndpointRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.EndpointResponse;
import dev.ncc.webhook.endpoint.EndpointDtos.RotateEndpointSecretRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.SetEndpointStatusRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.UpdateEndpointRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

  private final WebhookEndpointRepository repository;
  private final UrlSafetyPolicy urlSafetyPolicy;
  private final SecretCipher secretCipher;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public EndpointService(
      WebhookEndpointRepository repository,
      UrlSafetyPolicy urlSafetyPolicy,
      SecretCipher secretCipher,
      Clock clock,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.urlSafetyPolicy = urlSafetyPolicy;
    this.secretCipher = secretCipher;
    this.clock = clock;
    this.events = events;
  }

  @Transactional
  public EndpointResponse create(CreateEndpointRequest request) {
    String normalizedUrl = urlSafetyPolicy.validate(request.url()).toASCIIString();
    WebhookEndpoint endpoint =
        new WebhookEndpoint(
            UUID.randomUUID(),
            request.name().trim(),
            normalizedUrl,
            secretCipher.encrypt(request.secret()),
            Instant.now(clock));
    return EndpointResponse.from(repository.save(endpoint));
  }

  @Transactional(readOnly = true)
  public List<EndpointResponse> list() {
    return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
        .map(EndpointResponse::from)
        .toList();
  }

  @Transactional
  public EndpointResponse setStatus(UUID endpointId, SetEndpointStatusRequest request) {
    WebhookEndpoint endpoint = findVersioned(endpointId, request.expectedVersion());
    if (endpoint.isActive() == request.active()) {
      return EndpointResponse.from(endpoint);
    }

    endpoint.setActive(request.active());
    WebhookEndpoint saved = repository.saveAndFlush(endpoint);
    events.publishEvent(
        new EndpointStatusChanged(saved.getId(), saved.isActive(), saved.getVersion()));
    return EndpointResponse.from(saved);
  }

  @Transactional
  public EndpointResponse rotateSecret(UUID endpointId, RotateEndpointSecretRequest request) {
    WebhookEndpoint endpoint = findVersioned(endpointId, request.expectedVersion());
    endpoint.rotateSecret(secretCipher.encrypt(request.newSecret()));
    WebhookEndpoint saved = repository.saveAndFlush(endpoint);
    events.publishEvent(
        new EndpointSecretRotated(saved.getId(), saved.getVersion(), Instant.now(clock)));
    return EndpointResponse.from(saved);
  }

  @Transactional
  public EndpointResponse update(UUID endpointId, UpdateEndpointRequest request) {
    WebhookEndpoint endpoint = findVersioned(endpointId, request.expectedVersion());
    String normalizedName = request.name().trim();
    String normalizedUrl = urlSafetyPolicy.validate(request.url()).toASCIIString();
    boolean nameChanged = !endpoint.getName().equals(normalizedName);
    boolean urlChanged = !endpoint.getUrl().equals(normalizedUrl);
    if (!nameChanged && !urlChanged) {
      return EndpointResponse.from(endpoint);
    }

    endpoint.updateConfiguration(normalizedName, normalizedUrl);
    WebhookEndpoint saved = repository.saveAndFlush(endpoint);
    events.publishEvent(
        new EndpointConfigurationChanged(
            saved.getId(), saved.getVersion(), nameChanged, urlChanged, Instant.now(clock)));
    return EndpointResponse.from(saved);
  }

  private WebhookEndpoint findVersioned(UUID endpointId, long expectedVersion) {
    WebhookEndpoint endpoint =
        repository
            .findById(endpointId)
            .orElseThrow(() -> new NotFoundException("Webhook endpoint was not found"));
    if (endpoint.getVersion() != expectedVersion) {
      throw new VersionConflictException(
          "Webhook endpoint changed; refresh it before retrying the update");
    }
    return endpoint;
  }
}
