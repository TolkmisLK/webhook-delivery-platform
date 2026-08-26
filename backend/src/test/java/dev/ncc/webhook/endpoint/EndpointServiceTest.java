package dev.ncc.webhook.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.ncc.webhook.common.VersionConflictException;
import dev.ncc.webhook.config.SecretCipher;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import dev.ncc.webhook.endpoint.EndpointDtos.RotateEndpointSecretRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class EndpointServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

  @Test
  void encryptsRotatedSecretAndPublishesMetadataOnly() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    SecretCipher secretCipher = mock(SecretCipher.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    UUID endpointId = UUID.randomUUID();
    WebhookEndpoint endpoint =
        new WebhookEndpoint(
            endpointId,
            "primary",
            "https://example.com/hooks",
            "encrypted-old",
            NOW.minusSeconds(60));
    when(repository.findById(endpointId)).thenReturn(Optional.of(endpoint));
    when(secretCipher.encrypt("replacement-secret")).thenReturn("encrypted-new");
    when(repository.saveAndFlush(endpoint)).thenReturn(endpoint);
    EndpointService service = service(repository, secretCipher, events);

    var response =
        service.rotateSecret(endpointId, new RotateEndpointSecretRequest("replacement-secret", 0L));

    assertThat(endpoint.getEncryptedSecret()).isEqualTo("encrypted-new");
    assertThat(response.id()).isEqualTo(endpointId);
    assertThat(response.getClass().getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("secret", "encryptedSecret", "newSecret");
    verify(secretCipher).encrypt("replacement-secret");
    verify(repository).saveAndFlush(endpoint);

    ArgumentCaptor<EndpointSecretRotated> event =
        ArgumentCaptor.forClass(EndpointSecretRotated.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isEqualTo(new EndpointSecretRotated(endpointId, 0L, NOW));
    assertThat(event.getValue().toString())
        .doesNotContain("replacement-secret", "encrypted-new", "encrypted-old");
  }

  @Test
  void rejectsAStaleVersionBeforeEncrypting() {
    WebhookEndpointRepository repository = mock(WebhookEndpointRepository.class);
    SecretCipher secretCipher = mock(SecretCipher.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    UUID endpointId = UUID.randomUUID();
    WebhookEndpoint endpoint =
        new WebhookEndpoint(
            endpointId,
            "primary",
            "https://example.com/hooks",
            "encrypted-old",
            NOW.minusSeconds(60));
    when(repository.findById(endpointId)).thenReturn(Optional.of(endpoint));
    EndpointService service = service(repository, secretCipher, events);

    assertThatThrownBy(
            () ->
                service.rotateSecret(
                    endpointId, new RotateEndpointSecretRequest("replacement-secret", 1L)))
        .isInstanceOf(VersionConflictException.class);

    assertThat(endpoint.getEncryptedSecret()).isEqualTo("encrypted-old");
    verifyNoInteractions(secretCipher, events);
  }

  private EndpointService service(
      WebhookEndpointRepository repository,
      SecretCipher secretCipher,
      ApplicationEventPublisher events) {
    return new EndpointService(
        repository,
        mock(UrlSafetyPolicy.class),
        secretCipher,
        Clock.fixed(NOW, ZoneOffset.UTC),
        events);
  }
}
