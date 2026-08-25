package dev.ncc.webhook.endpoint;

import dev.ncc.webhook.config.SecretCipher;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import dev.ncc.webhook.endpoint.EndpointDtos.CreateEndpointRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.EndpointResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {

  private final WebhookEndpointRepository repository;
  private final UrlSafetyPolicy urlSafetyPolicy;
  private final SecretCipher secretCipher;
  private final Clock clock;

  public EndpointService(
      WebhookEndpointRepository repository,
      UrlSafetyPolicy urlSafetyPolicy,
      SecretCipher secretCipher,
      Clock clock) {
    this.repository = repository;
    this.urlSafetyPolicy = urlSafetyPolicy;
    this.secretCipher = secretCipher;
    this.clock = clock;
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
}
