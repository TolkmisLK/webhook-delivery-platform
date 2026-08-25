package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class WebhookHttpClient {

  private final HttpClient client;
  private final HmacSigner signer;
  private final DeliveryProperties properties;
  private final Clock clock;

  WebhookHttpClient(
      HttpClient client, HmacSigner signer, DeliveryProperties properties, Clock clock) {
    this.client = client;
    this.signer = signer;
    this.properties = properties;
    this.clock = clock;
  }

  DeliveryResult deliver(DeliveryContext context) {
    long timestamp = Instant.now(clock).getEpochSecond();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(context.endpointUrl()))
            .timeout(properties.getRequestTimeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", "NCC-Webhook-Delivery/0.2")
            .header("X-Webhook-Id", context.eventId().toString())
            .header("X-Webhook-Type", context.eventType())
            .header("X-Webhook-Timestamp", Long.toString(timestamp))
            .header("X-Webhook-Signature", signer.sign(context.secret(), timestamp, context.body()))
            .POST(HttpRequest.BodyPublishers.ofString(context.body(), StandardCharsets.UTF_8))
            .build();

    long started = System.nanoTime();
    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      String excerpt = readLimited(response.body());
      int status = response.statusCode();
      boolean successful = status >= 200 && status < 300;
      boolean retryable = status == 408 || status == 429 || status >= 500;
      return new DeliveryResult(
          successful,
          retryable,
          status,
          successful ? null : "Target returned HTTP " + status,
          excerpt,
          elapsedMillis(started));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new DeliveryResult(
          false, true, null, "Delivery was interrupted", null, elapsedMillis(started));
    } catch (IOException | RuntimeException exception) {
      return new DeliveryResult(
          false,
          true,
          null,
          exception.getClass().getSimpleName() + ": " + safeMessage(exception),
          null,
          elapsedMillis(started));
    }
  }

  private String readLimited(InputStream input) throws IOException {
    try (input) {
      byte[] bytes = input.readNBytes(properties.getMaxResponseBytes() + 1);
      boolean truncated = bytes.length > properties.getMaxResponseBytes();
      int length = Math.min(bytes.length, properties.getMaxResponseBytes());
      String value = new String(bytes, 0, length, StandardCharsets.UTF_8);
      return truncated ? value + "…" : value;
    }
  }

  private long elapsedMillis(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }

  private String safeMessage(Exception exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? "request failed" : message;
  }
}
